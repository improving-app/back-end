FROM openjdk:11

ENV SBT_VERSION 1.5.5

RUN curl -L -o sbt-$SBT_VERSION.zip https://github.com/sbt/sbt/releases/download/v$SBT_VERSION/sbt-$SBT_VERSION.zip

RUN unzip sbt-$SBT_VERSION.zip -d ops

WORKDIR /temp

ADD . /temp

CMD /ops/sbt/bin/sbt test

EXPOSE 8080

EXPOSE 8558

EXPOSE 25520
