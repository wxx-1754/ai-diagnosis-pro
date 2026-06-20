# AI Diagnosis Frontend

极简诊断页面，使用 Anime.js 做加载、结果切换和链路流动动效。

## 启动

```bash
npm install
npm run dev
```

默认访问：

```text
http://127.0.0.1:5173
```

页面默认连接：

```text
http://localhost:9001
```

如果后端端口或地址不同，请创建 `.env.local`：

```text
VITE_API_BASE_URL=http://localhost:9001
```

然后运行 `npm run dev`。

顶部的服务与环境选择项由后端 `/api/app-instances/options` 接口从 `app_instance` 表加载。
