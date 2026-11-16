FROM gradle:8.13-jdk21

USER root
ARG LOCAL_UID=1000
ARG LOCAL_GID=1000
RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
RUN groupmod -o -g "$LOCAL_GID" gradle \
    && usermod -o -u "$LOCAL_UID" -g "$LOCAL_GID" gradle \
    && chown -R gradle:gradle /app /home/gradle
COPY --chown=gradle:gradle . .
USER gradle
ENV JAVA_TOOL_OPTIONS="-Djava.awt.headless=true"
CMD ["gradle", "run", "--no-daemon", "--console=plain", "-Poutput=resultados/partida.csv"]
