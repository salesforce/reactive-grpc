Overview
========

This demo shows the use of Reactive-gRPC in a chat application.
The code is written in Kotlin and the server is based on Spring Boot 2.

Usage
=====

## Starting the server

The server is a regular Spring-Boot 2 app and can be run from maven
```sh
mvn spring-boot:run -pl demos/ReactorChat-Kotlin/ReactorChat-Server-Kt
```

## Starting clients

First package the client app
```sh
mvn package -pl demos/ReactorChat-Kotlin/ReactorChat-Client-Kt
```
Then run it as a regular jar
```sh
java -jar demos/ReactorChat-Kotlin/ReactorChat-Client-Kt/target/ReactorChat-Client-Kt-*.jar
```
You can start as many clients as you want and make them chat between each other.