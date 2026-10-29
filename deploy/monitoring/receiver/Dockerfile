FROM alpine:latest@sha256:be2656061b536c1883c4034790e21d0a09c5aaaf51968b89368a3c0371ea2c9a
RUN adduser -D -u 10001 receiver \
    && mkdir -p /var/log/alert-receiver \
    && chown -R receiver:receiver /var/log/alert-receiver
COPY start.sh /receiver/start.sh
COPY handler /receiver/handler
RUN chmod 0755 /receiver/start.sh /receiver/handler
USER receiver
EXPOSE 8099
CMD ["sh", "/receiver/start.sh"]
