# 提交记录

本文档记录 HitModel3.0 项目的 Git 提交和推送信息。

记录范围：截至初始提交 `e5c9e16` 的项目打包与推送信息；本文档后续更新产生的提交以 Git 历史为准。

## 仓库信息

- 本地路径：`D:\Java_Space\HitModel3.0`
- 远程仓库：`https://github.com/chuyds/HitModel3.0.git`
- 当前分支：`main`
- 跟踪分支：`origin/main`

## 提交列表

| 序号 | 提交号 | 提交时间 | 作者 | 提交说明 | 状态 |
| --- | --- | --- | --- | --- | --- |
| 1 | `e5c9e1689cb3fecb1dfaf483c62894741f7e7b65` | `2026-08-11T14:47:42+08:00` | `Ahnuyds <364203330@qq.com>` | `Initial commit` | 已推送到 `origin/main` |

## 初始提交内容

提交：`e5c9e16 Initial commit`

主要内容：

- 初始化 Git 仓库并使用 `main` 分支。
- 提交 Spring Boot 主项目源码、测试代码和 Maven Wrapper。
- 提交 OPC 采集相关源码目录 `opc-collector-source/`。
- 提交运行脚本目录 `scripts/`。
- 提交项目依赖的本地 jar 文件目录 `src/main/resources/libs/`。
- 新增 `.gitignore`，排除 `target/`、`.idea/`、本地环境变量文件和日志文件。
- 新增 `.gitattributes`，规范脚本、批处理文件和 jar 文件的 Git 属性。
- 新增 `.env.example`，提供数据库和 OPC 配置变量模板。
- 新增 `README.md`，记录项目说明、配置方式、启动方式和接口列表。
- 将 `application.yml` 中的 OPC 密码改为通过环境变量 `OPC_PASSWORD` 配置。
- 将 `pom.xml` 中本地 jar 的绝对路径改为项目相对路径。
- 合并重复的 `spring-boot-maven-plugin` 配置。

变更统计：

- 文件数：62
- 新增行数：5578
- 初始提交包含源码、测试、脚本、配置和项目内 jar 依赖。

## 验证记录

执行过干净构建测试：

```bat
mvnw.cmd clean test
```

结果：

- 测试总数：9
- 失败：0
- 错误：0
- 跳过：0
- 构建结果：`BUILD SUCCESS`

## 备注

- Maven 仍提示 `systemPath` 指向项目内 jar 文件。当前项目可以正常构建，但后续如果要作为依赖被其他项目引用，建议把这些 jar 发布到私有 Maven 仓库或改为标准依赖管理方式。
- `target/`、`.idea/` 和本地 `.env` 文件不会提交到 Git。
