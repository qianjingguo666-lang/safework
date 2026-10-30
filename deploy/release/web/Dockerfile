# 前端镜像（发布包）：dist 已经在自己电脑上用 npm run build 编译好，这里只放进 Nginx
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY dist /usr/share/nginx/html
EXPOSE 80
