FROM nginx:alpine@sha256:892bb2152464f0c5d8972593a5f5afaf8054552580b699d95f8ff4af08c2d33d
RUN rm -rf /usr/share/nginx/html/* /etc/nginx/conf.d/default.conf
COPY deploy/web/nginx.conf /etc/nginx/nginx.conf
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=3 CMD wget -q -O - http://127.0.0.1:8080/healthz | grep -q ok
