FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

# Copy all project files explicitly
COPY Gen.java Gen.html Gen.css /app/

# Compile strictly the single Java file
RUN javac -encoding UTF-8 Gen.java

EXPOSE 9090

CMD ["java", "Gen"]
