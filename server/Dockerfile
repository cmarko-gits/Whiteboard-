# Build first: ./gradlew :server:installDist
# Pin the JRE image by digest in the deployment pipeline and scan it before promotion.
FROM eclipse-temurin:21-jre-jammy
RUN groupadd --gid 10001 whiteboard && useradd --uid 10001 --gid 10001 --no-create-home whiteboard
WORKDIR /opt/whiteboard
COPY --chown=10001:10001 server/build/install/server/ ./
USER 10001:10001
EXPOSE 8080
ENV WHITEBOARD_ENV=production
ENTRYPOINT ["/opt/whiteboard/bin/server"]
