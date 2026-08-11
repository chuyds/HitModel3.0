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
        model_code="final_cooler_temp_control_model",
        timeout=3
    ):
        self.url = url
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
                print("四个数据[DB] 数据写入成功")
            else:
                print(f"四个数据[DB] 写入失败 {resp.status_code}: {resp.text}")
        except Exception as e:
            print("[DB] 请求异常:", e)




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
    

   
"""    
    # 可视化函数，生成并保存图片
def generate_and_save_plot(u_matrix_raw, y_matrix_raw, predictions_store, timestamp):
    plt.figure(figsize=(12, 7))

    # 绘制原始数据 (灰色细线)
    plt.plot(range(len(u_matrix_raw)), 
             y_matrix_raw, 
             color='#D3D3D3', linewidth=1.0, label='Raw Data (Noisy)')

    # 绘制预测点 (蓝色圆圈)
    pred_times = [p[0] + 1 for p in predictions_store]
    pred_values = [p[1] for p in predictions_store]
    plt.plot(pred_times, pred_values, 'bo', markersize=3, label='Prediction (K=1)')

    # 绘制当前时刻的滤波值 (红色点)
    filter_basis_times = [t for t in range(len(u_matrix_raw))]
    filter_basis_values = y_matrix_raw
    plt.plot(filter_basis_times, filter_basis_values, 'r.', markersize=6, label='Current Filtered State')

    # 保存图片到文件夹
    save_path = f"armaxMPC_picture/forecast_MPC.png"
    plt.xlim(0, len(u_matrix_raw))
    plt.ylim(np.min(y_matrix_raw) - 0.5, np.max(y_matrix_raw) + 0.5)
    plt.xlabel('Time (Samples)')
    plt.ylabel('Gas Temperature Out')
    plt.title('Rolling Simulation: Real-time Forecast with ARMAX')
    plt.grid(True, which='both', linestyle='--', alpha=0.7)
    plt.legend(loc='best')

    plt.savefig(save_path, dpi=300, bbox_inches='tight')
    plt.close()
    print(f"Plot saved to: {save_path}")
"""


# ==========================================
# 4. 结果可视化 
# ==========================================

# 解决中文乱码
plt.rcParams['font.sans-serif'] = ['SimHei']
plt.rcParams['axes.unicode_minus'] = False
os.makedirs('armaxMPC_picture', exist_ok=True)

"""
def generate_and_save_plot(u_matrix_raw, y_matrix_raw, predictions_store, timestamp):
    sim_start_idx=20
    sim_end_idx=493
    plot_step_2=20
    plot_indices = range(sim_start_idx, sim_end_idx, plot_step_2)
    plt.figure(figsize=(12, 8))

    # 子图1：温度（核心用predictions_store取8个点，自动对应横坐标）
    plt.subplot(2, 1, 1)
    # 画历史温度曲线
    plt.plot(range(sim_start_idx, sim_end_idx), 
             y_filtered[sim_start_idx:sim_end_idx], 
             'b-', alpha=0.3, label='Historical')
        
    # 关键：从predictions_store取8个点，x=step  y=y_pred 完美对应
    if len(predictions_store) >= 20:
        plot_data = predictions_store[:20]  # 取前8个，要最新8个换[-8:]
        x_steps = [t[0] for t in plot_data]  # 横坐标就是记录的step（40/60/80/100...）
        y_sim_plot = [t[1] for t in plot_data]# 纵坐标对应y_pred
        plt.plot(x_steps, y_sim_plot, 'r.-', linewidth=2, label='MPC Simulation')
        plt.xticks(x_steps) # 强制显示对应step，不混乱
    elif predictions_store:
        # 不足8个也画，不报错
        x_steps = [t[0] for t in predictions_store]
        y_sim_plot = [t[1] for t in predictions_store]
        plt.plot(x_steps, y_sim_plot, 'r.-', linewidth=2, label='MPC Simulation')
        plt.xticks(x_steps)
        print(f"预测数据不足20组，当前{len(predictions_store)}组")
    else:
        print("暂无预测数据")
    # 画 Setpoint
    plt.axhline(y=T_gas_set_point, color='k', linestyle='--', label='Setpoint')
    plt.title('Gas Outlet Temperature')
    plt.ylabel('°C')
    plt.legend()
    plt.grid(True)
    plt.xlim(sim_start_idx, sim_end_idx)
    save_path =f"armaxMPC_picture/forecast_{timestamp}MPC.png"
    plt.gcf().canvas.draw()
    plt.savefig(save_path, dpi=300, bbox_inches='tight')
    print(f"Result saved to {save_path}")
    
    # 子图 2: 流量
    # 画实际流量
    # 从存储列表中拆分x轴、U4base、U4pred
    x_axis = [item[0] for item in data_list]
    u4_base_plot = [item[1] for item in data_list]
    u4_pred_plot = [item[2] for item in data_list]

    # ===================== 4. 绘图（从x=40开始，每隔20步画点）=====================
    plt.figure(figsize=(10, 6), dpi=100)

    # 绘制U4base（折线+散点，突出每个20步的点）
    plt.plot(x_axis, u4_base_plot, 'b-', label='U4_base', linewidth=1.5)
    plt.scatter(x_axis, u4_base_plot, color='blue', s=50, zorder=5)  # zorder让点在曲线上方

    # 绘制U4pred（折线+散点）
    plt.plot(x_axis, u4_pred_plot, 'r--s', label='U4_pred', linewidth=1.5, markersize=6)

    # 设置x轴刻度（强制显示每隔20步的点）
    plt.xticks(x_axis, rotation=0)
    plt.xlim(start_x - 5, x_axis[-1] + 5)  # x轴范围左右留5个步长余量

    plt.title('Cooling Water Flow Rate')
    plt.ylabel('kg/s')
    plt.legend()
    plt.grid(True,alpha=0.3)
    plt.xlim(sim_start_idx, sim_end_idx)

    save_dir =f"armaxMPC_picture/forecast_{timestamp}FLOW.png"
    plt.gcf().canvas.draw()
    plt.savefig(save_dir, dpi=300, bbox_inches='tight')
    print(f"Result saved to {save_dir}")
    plt.close()
"""




    

u4_base=None
w_y = 1.0  
w_u = 0.1   
def predict_armax_one_step(y_hist_centered, u_hist_centered):
    """
    计算 ARMAX 下一步预测 (去均值后).
    y_hist_centered: [y(t), y(t-1)...] (reverse order)
    u_hist_centered: [4, lag] -> [u(t), u(t-1)...]
    """
    # AR part,
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
        FI_33111A = float(data["FT_33111A_AV"])  # 低温水进口流量
        FI_33111B = float(data["FT_33111B_AV"])  # 低温水进口流量
        if FI_33111A>0:
            water_flow=FI_33111A
        else:
            water_flow=FI_33111B

        TT_82201A = float(data["TT_82201B_AV"])  # 终冷塔A进口煤气温度
        TT_82202A = float(data["TT_82202B_AV"])  # 终冷塔A出口煤气温度

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
        u_matrix_raw.append([TT_82221, TT_82201A, total_gas_flow, water_flow])  # u1, u2, u3, u4
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
            
            
            # 预测结果
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
            print(f"✅k+20时刻预测流量：{u4_pred}")
                     # ===== 写入 HIT 模型数据库 =====
            db_client.write([
                {"dataType": "temperaturePrediction", "value": float(y_pred)},
                {"dataType": "waterFlow", "value": float(u4_pred)},
                {"dataType": "temperature", "value": float(TT_82202A)},
                {"dataType": "waterFlowPrediction", "value": float(u4_pred)}
            ])
"""
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
       
"""   
        current_timestamp=time.time()   
        if current_timestamp - last_timestamp >=500:
           generate_and_save_plot(u_matrix_raw, y_matrix_raw, predictions_store, int(current_timestamp))
           last_timestamp = current_timestamp
           print("1")
"""

            
