FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY . .

RUN javac Main.java Shelter.java

EXPOSE 10000

CMD ["java", "--add-modules", "jdk.httpserver", "Main"]
