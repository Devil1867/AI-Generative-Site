# Use lightweight OpenJDK image
FROM openjdk:17-jdk-slim

# Set working directory inside container
WORKDIR /app

# Copy project files
COPY . /app

# Compile the Java server
RUN javac Gen.java

# Expose port and run
EXPOSE 9090
CMD ["java", "Gen"]