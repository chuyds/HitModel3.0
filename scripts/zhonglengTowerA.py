import socket
import time
import json
import joblib
import numpy as np
import pandas as pd
from collections import deque
import pickle
import os
import matplotlib.pyplot as plt
import sippy
import sys
import scipy
from scipy.optimize import minimize
from datetime import datetime, timedelta
import requests

import torch
import torch.nn as nn
import cvxpy as cp

# =========================
# 兼容：pickle 里引用的是 sippy_unipi
# =========================
sys.modules["sippy_unipi"] = sippy

den_A = np.array([-1.98540588,  0.98540448])
num_B = [np.array([ 0.01555906, -0.01554878]), np.array([ 0.00816457, -0.00816462]), np.array([ 0.02346738, -0.0236423 ]), np.array([-0.00408491,  0.00408572])]

# 数据读取和预处理函数（来自predict.py）
def load_and_slice(path, col_idx, start_idx, end_idx, sample_step):
    try:
        df = pd.read_csv(path, encoding='utf-8')
    except:
        df = pd.read_csv(path, encoding='gbk')
    return df.iloc[start_idx:end_idx:sample_step, col_idx].values.reshape(-1, 1)

# 创建保存图片的文件夹
if not os.path.exists("armaxMPC_picture"):
    os.makedirs("armaxMPC_picture")




# =========================
# 0.5) HIT 模型数据库写入
# =========================
class HitModelDBClient:
    def __init__(
        self,
        url="http://localhost:39200/jlxg/hit-model/sink-data",
        status_url="http://localhost:39200/jlxg/hit-model/controlStatus",
        stop_start_url="http://10.51.35.249:8083/api/model/stopOrStart",
        model_code="final_cooler_temp_control_model",
        timeout=3
    ):
        self.url = url
        self.status_url = status_url
        self.stop_start_url = stop_start_url
        self.model_code = model_code
        self.timeout = timeout
        self.headers = {"Content-Type": "application/json"}

    def write(self, data_items: list):
        """
        data_items 示例:
        [
          {"dataType": "temperaturePrediction", "value": 32.1},
          {"dataType": "waterFlow", "value": 88.5}
        ]
        """
        payload = {
            "modelCode": self.model_code,
            "data": data_items
        }

        try:
            resp = requests.post(
                self.url,
                json=payload,
                headers=self.headers,
                timeout=self.timeout
            )
            if resp.status_code == 200:
                print("[DB] 数据写入成功")
            else:
                print(f"[DB] 写入失败 {resp.status_code}: {resp.text}")
        except Exception as e:
            print("[DB] 请求异常:", e)
    
    # 发送控制状态请求
    def write_control_status(self, status: int):
        payload = {
            "modelCode": self.model_code,
            "controlStatus": status
        }
        try:
            resp = requests.post(
                self.status_url,
                json=payload,
                headers=self.headers,
                timeout=self.timeout
            )
            if resp.status_code == 200:
                print(f"[DB] 控制状态 {status} 写入成功")
            else:
                print(f"[DB] 控制状态写入失败 {resp.status_code}: {resp.text}")
        except Exception as e:
            print("[DB] 控制状态请求异常:", e)

    def stop_smart_control(self):
        payload = {
            "modelCode": self.model_code,
            "status": 2
        }
        try:
            resp = requests.post(
                self.stop_start_url,
                json=payload,
                headers=self.headers,
                timeout=self.timeout
            )
            if resp.status_code == 200:
                print("[DB] 停止智能控制请求发送成功")
            else:
                print(f"[DB] 停止智能控制请求失败 {resp.status_code}: {resp.text}")
        except Exception as e:
            print("[DB] 停止智能控制请求异常:", e)


# 实时数据获取和预测整合
class JavaOPCBridge:
    """
    方案 B：
    - Java 统一提供服务
    - Python 只做 Client
    """

    def __init__(
        self,
        read_host="127.0.0.1",
        read_port=50008,
        write_host="127.0.0.1",
        write_port=50009,
        retry_sec=2,
    ):
        self.read_host = read_host
        self.read_port = read_port
        self.write_host = write_host
        self.write_port = write_port
        self.retry_sec = retry_sec

        self._connect_read()
        self._connect_write()

    # ---------- 连接 Java 实时流 ----------
    def _connect_read(self):
        while True:
            try:
                print(f"[PY] 连接 Java 实时流 {self.read_host}:{self.read_port}")
                sock = socket.create_connection(
                    (self.read_host, self.read_port), timeout=3
                )
                self.read_stream = sock.makefile()
                print("[PY] 已连接 Java 实时流")
                return
            except Exception as e:
                print("[PY] 实时流未就绪，重试中...", e)
                time.sleep(self.retry_sec)

    # ---------- 连接 Java 写入 ----------
    def _connect_write(self):
        while True:
            try:
                print(f"[PY] 连接 Java 写入 {self.write_host}:{self.write_port}")
                sock = socket.create_connection(
                    (self.write_host, self.write_port), timeout=3
                )
                self.write_file = sock.makefile("rwb")
                print("[PY] 已连接 Java 写入")
                return
            except Exception as e:
                print("[PY] 写入服务未就绪，重试中...", e)
                time.sleep(self.retry_sec)

    # ---------- 实时数据 ----------
    def realtime(self):
        for line in self.read_stream:
            try:
                yield json.loads(line)
            except Exception as e:
                print("[PY] JSON 解析失败:", e)

    # ---------- 写 OPC ----------
    def write(self, tag, value, dtype="float"):
        try:
            v = float(value)
            msg = f"{tag},{v},{dtype}\n"
            self.write_file.write(msg.encode("utf-8"))
            self.write_file.flush()
            print(f"[PY->OPC] {tag} = {v} ({dtype})")
        except Exception as e:
            print("[PY] 写入失败:", e)

# 实时滤波和预测
def real_time_predict(data, u_filtered, y_filtered, predict_step):

    # 模型预测（ARMA模型）0
    # import pdb; pdb.set_trace()
    A_coeffs = den_A
    B_coeffs_list = [num_B[ch] for ch in range(4)]

    hist_y_centered = [y_filtered[-(i * predict_step + 1)] for i in range(len(A_coeffs))]
    ar_term = np.dot(A_coeffs, hist_y_centered)
    
    forcing_term = 0
    for ch in range(4):
        b_poly = B_coeffs_list[ch]
        u_future_centered = u_filtered[-1][ch] 
        u_seq_centered = [u_future_centered, u_filtered[-1][ch]]
        # import pdb; pdb.set_trace()
        forcing_term += np.dot(b_poly, u_seq_centered)

    y_pred_centered = forcing_term - ar_term
    y_pred_final = y_pred_centered
    return y_pred_final
    

# 解决中文乱码
plt.rcParams['font.sans-serif'] = ['SimHei']
plt.rcParams['axes.unicode_minus'] = False
os.makedirs('armaxMPC_picture', exist_ok=True)


#应用ARMAX模型
u4_base=None
w_y = 1.0  
w_u = 0.1   
def predict_armax_one_step(y_hist_centered, u_hist_centered):
    """
    计算 ARMAX 下一步预测 (去均值后).
    y_hist_centered: [y(t), y(t-1)...] (reverse order)
    u_hist_centered: [4, lag] -> [u(t), u(t-1)...]
    """
    # AR part
    ar_val = 0.0
    for i, a_coeff in enumerate(den_A):
        if i < len(y_hist_centered):
            ar_val += a_coeff * y_hist_centered[i]
            
    # X part
    x_val = 0.0
    for ch in range(4):
        b_coeffs = num_B[ch]
        u_ch_hist = u_hist_centered[ch]
        for j, b_coeff in enumerate(b_coeffs):
            if j < len(u_ch_hist):
                x_val += b_coeff * u_ch_hist[j]
                
    return x_val - ar_val
    
T_gas_set_point=24
# --- 辅助函数：计算 MPC Cost (对应 fmincon 的 objective) ---
def calculate_mpc_cost(U_deltas, past_y_phys, past_u_phys, future_disturbances_phys):
    """
    U_deltas: 优化变量 [delta_1, delta_2, delta_3, delta_4]
    past_y_phys: 历史 y (物理值) [y(t), y(t-1)...]
    past_u_phys: 历史 u (物理值) [u(t), u(t-1)...]
    future_disturbances_phys: 未来已知的扰动 [3, horizon]
    """
    cost = 0.0
    
    # 初始化递归所需的临时 buffer (去均值)
    curr_y_hist = list((past_y_phys).flatten()) # [y(t)_c, y(t-1)_c...]
    
    # u_hist 结构: List of lists, 每个通道 [u(t)_c, u(t-1)_c...]
    curr_u_hist = []
    u_phys_centered = past_u_phys.T
    for ch in range(4):
        curr_u_hist.append(list(u_phys_centered[ch, :]))
        
    # 当前控制量 (用于累加 delta)
    current_u_act = past_u_phys[-1, 3] # u4(t)
    
    for k in range(predict_length):
        delta = U_deltas[k]
        
        # 1. 计算这一步的输入 u(t+k)
        # 扰动项 (前3列)
        u_dist_k = future_disturbances_phys[:, k]
        # 控制项 (第4列)
        current_u_act += delta
        u_ctrl_k = current_u_act
        
        u_vector_k_phys = np.append(u_dist_k, u_ctrl_k)
        u_vector_k_centered = u_vector_k_phys
        
        # 2. 将新输入插入历史 (作为 u(t+k))
        # 注意 ARMAX 逻辑:
        # 如果 nk=1, y(t+1) 取决于 u(t). 
        # 我们这里的 predict_armax_one_step 是基于 [u(new), u(old)...] 计算 y(new)
        # 所以把 u_vector_k_centered 放在最前面是正确的
        for ch in range(4):
            curr_u_hist[ch].insert(0, u_vector_k_centered[ch])
            
        # 3. 预测 y(t+k+1) (模型步长)
        # 这里的命名有点混淆，但逻辑是：利用当前状态预测下一步
        y_pred_centered = predict_armax_one_step(curr_y_hist, curr_u_hist)
        y_pred_phys = y_pred_centered
        
        # 4. 累加 Cost
        # Tracking Error
        cost += w_y * (y_pred_phys - T_gas_set_point)**2
        # Control Effort
        cost += w_u * (delta**2)
        
        # 5. 更新 y 历史，准备下一次递归
        curr_y_hist.insert(0, y_pred_centered)
        
        # 限制 buffer 大小
        if len(curr_y_hist) > 10: curr_y_hist.pop()
        for ch in range(4):
            if len(curr_u_hist[ch]) > 10: curr_u_hist[ch].pop()
            
    return cost

# 主程序
if __name__ == "__main__":
    bridge = JavaOPCBridge()
    # 初始化输入矩阵
    u_matrix_raw = []
    y_matrix_raw = []
    predictions_store = []
        
    last_timestamp = time.time() # 用于生成图片文件名的时间戳
        
    u_filtered = []
    y_filtered = []
    y_pred_array = []

    
    # 滤波器参数 (对应 MATLAB)
    fs = 1000.0
    fc = 1.0              # 截止频率 1Hz
    dt = 1.0 / fs
    RC = 1.0 / (2 * np.pi * fc)
    alpha = dt / (RC + dt) # 滤波系数
        
    rough_gas = 0.49
        
    predict_length=4
    predict_step = 20
    step = 0 
    db_client = HitModelDBClient()#写入服务器
    for data in bridge.realtime():

        step += 1
         # 获取并处理新数据 

        TT_82201A = float(data["TT_82201A_AV"])  # 终冷塔A进口煤气温度
        TT_82202A = float(data["TT_82202A_AV"])  # 终冷塔A出口煤气温度

        FI_33111A = float(data["FT_33111A_AV"])  # 低温水A进口流量
        FI_33111B = float(data["FT_33111B_AV"])  # 低温水A进口流量


        TT_82221 = float(data["TT_82221_AV"])    # 低温水进口温度
        FT_82204 = float(data["FT_82204_AV"])    # 进口煤气流量
        FIR_81003A = float(data["FIR_81003A_AV"])  # 进口煤气流量
        FIR_81003B = float(data["FIR_81003B_AV"])  # 进口煤气流量
        FT_88888 = float(data["FT_88888_AV"])  # 进口煤气流量
        
        print(f'step:{step} temp:{TT_82202A}')

        # 计算进口煤气流量的总和
        total_gas_flow = FT_82204 + FIR_81003A + FIR_81003B + FT_88888
        total_gas_flow = total_gas_flow / 3600 * rough_gas
        # import pdb; pdb.set_trace()
        # 整合数据
        # u_matrix_raw.append([TT_82221, TT_82201A, total_gas_flow, FT_33111A])  # u1, u2, u3, u4
        u_matrix_raw.append([TT_82221, TT_82201A, total_gas_flow, FI_33111A])  # u1, u2, u3, u4
        y_matrix_raw.append(TT_82202A)  # y = 终冷塔A出口煤气温度

        if len(u_matrix_raw) > 2:
            index_u_filtered = []
            for i in range(len(u_matrix_raw[-1])):
                index_u_filtered.append(alpha * u_matrix_raw[-1][i] + (1 - alpha) * u_filtered[-1][i])
            u_filtered.append(index_u_filtered)
            y_filtered.append(alpha * y_matrix_raw[-1] + (1 - alpha) * y_filtered[-1])
        else:
            u_filtered.append(u_matrix_raw[-1])
            y_filtered.append(y_matrix_raw[-1])
        

        if  step > 20 and step % predict_step == 0:
            # 应用预测模型
            y_pred = real_time_predict(data, u_filtered, y_filtered, predict_step)
            predictions_store.append((len(u_matrix_raw), y_pred))
            y_pred_array.append(y_pred)
            
            
            # 将预测结果写入OPC
            print(f"实际温度: {TT_82202A} ")
            print(f"预测温度: {y_pred} at timestamp {step + 20}")
            #import pdb; pdb.set_trace()
            hist_indices = [-1 - k*20 for k in range(2)] # 回溯2个模型步, 最新一帧和第20帧
            past_y_vals = np.array([y_filtered[i] for i in hist_indices]) # [y(t), y(t-1)...]
            past_u_vals = np.array([u_filtered[i] for i in hist_indices]) # [u(t), u(t-1)...]
            #import pdb; pdb.set_trace()

            # 取初始k时刻流量
            u4_base = u_filtered[-1][3] 
            print(f"✅k时刻实际流量：{u4_base}")
            
            # --- 3. 准备未来扰动 (Disturbance Forecast) ---
            # 假设未来扰动等于当前的滤波值 (Zero Order Hold)
            curr_disturbances = np.array(u_filtered[-1][0:3]) # [t] 时刻的 u1,u2,u3
            future_disturbances = np.tile(curr_disturbances.reshape(-1, 1), (1, predict_length))
            
            # --- 4. 优化器 (fmincon -> minimize) ---
            x0 = np.zeros(predict_length) # 初始猜测
            bounds = [(-10, 10) for _ in range(predict_length)]

            # 构造 Objective Function
            fun = lambda U: calculate_mpc_cost(U, past_y_vals, past_u_vals, future_disturbances)
            # import pdb; pdb.set_trace()
            res = minimize(fun, x0, method='SLSQP', bounds=bounds, tol=1e-8)
            U_optimal = res.x
            print(f'U_optimal:{U_optimal}')
            u4_pred = u4_base + U_optimal[0]
            bridge.write("工大OPC.AO.HIT_11115_SP", u4_pred)
            print(f"✅k+20时刻预测流量：{u4_pred}")

            # ===== 新增：判断并发送控制状态 =====
            if FI_33111B > FI_33111A or FI_33111A < 0:
                control_status = 1
            else:
                control_status = 2
            db_client.write_control_status(control_status)
            if control_status == 1:
                db_client.stop_smart_control()

                     # ===== 写入 HIT 模型数据库 =====
            db_client.write([
                {
                "dataType": "temperaturePrediction",
                "value": float(y_pred)
                },
                {
                "dataType": "temperature",
                "value": float(TT_82202A)
                },
                {
                "dataType": "waterFlow",
                "value": float(u4_base)
                },
                {
                "dataType": "waterFlowPrediction",
                "value": float(u4_pred)
                }
            ])
       
"""   
        current_timestamp=time.time()   
        if current_timestamp - last_timestamp >=500:
           generate_and_save_plot(u_matrix_raw, y_matrix_raw, predictions_store, int(current_timestamp))
           last_timestamp = current_timestamp
           print("1")
"""

            
