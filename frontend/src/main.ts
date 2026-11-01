import { createApp } from "vue";
import { createRouter, createWebHashHistory } from "vue-router";
import ElementPlus from "element-plus";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import "element-plus/dist/index.css";
import App from "./App.vue";
import Workspace from "./views/Workspace.vue";
import "./style.css";
const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: "/", redirect: "/overview" },
    ...["overview", "history", "statistics", "stations", "sources"].map(
      (path) => ({
        path: `/${path}`,
        component: Workspace,
        props: { view: path },
      }),
    ),
    { path: "/:pathMatch(.*)*", redirect: "/overview" },
  ],
});
createApp(App).use(router).use(ElementPlus, { locale: zhCn }).mount("#app");
