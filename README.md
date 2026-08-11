# HitModel

HitModel 是一个 Spring Boot 项目，用于模型控制、OPC 数据读写和实时 OPC 数据广播。

## 环境要求

- JDK 17
- Maven Wrapper 或 Maven 3.x
- MySQL

## 配置

应用默认端口为 `8083`。运行前建议通过环境变量配置数据库和 OPC 连接信息：

| 变量名 | 默认值 | 说明 |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3306/jlxg?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai` | 数据库连接地址 |
| `SPRING_DATASOURCE_USERNAME` | `root` | 数据库用户名 |
| `SPRING_DATASOURCE_PASSWORD` | 空 | 数据库密码 |
| `OPC_HOST` | `192.168.14.14` | OPC 主机地址 |
| `OPC_DOMAIN` | 空 | OPC 域 |
| `OPC_USER` | `administrator` | OPC 用户名 |
| `OPC_PASSWORD` | 空 | OPC 密码 |
| `OPC_CLSID` | `001AAAA6-FB54-4627-84B2-8777379E5868` | OPC CLSID |

## 启动

Windows:

```bat
mvnw.cmd spring-boot:run
```

Linux/macOS:

```bash
./mvnw spring-boot:run
```

## 打包

```bash
./mvnw clean package
```

打包产物会生成在 `target/` 目录，该目录不会提交到 Git。

## 接口

基础路径：`/api/model`

- `POST /stopOrStart`
- `POST /writeData`
- `POST /readData`
