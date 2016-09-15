FROM java:8

COPY kotlinc /usr/app/kotlinc
COPY bin /usr/app/bin
COPY lib /usr/app/lib
WORKDIR /usr/app/

RUN chmod +x bin/KotlinEvalSlackBot
RUN chmod +x kotlinc/bin/kotlinc

CMD "/usr/app/bin/KotlinEvalSlackBot"