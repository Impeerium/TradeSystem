# Impeerium TradeSystem

This fork tracks [upstream TradeSystem](https://github.com/erikzimmermann/TradeSystem)
and adds Minecraft 26.3 compatibility while retaining Minecraft 26.2 support.

The shared `2.6.3_Hotfix-9-impeerium.1` build includes the latest upstream
`develop` changes and pins [CodingAPI PR #37](https://github.com/erikzimmermann/CodingAPI/pull/37)
at commit `9a7230cd80502f6e6a092370370e5095cca49851`. That patch updates sign text
collections and the front-side sign editor API. It is an unmerged upstream fix,
not a newer official TradeSystem release.

Build with JDK 21 and Maven: `mvn -B -ntp clean verify`. The installable shaded
JAR is in `TradeSystem-Bundle/target/`. See [AGENTS.md](AGENTS.md) for deployment
and [verification/README.md](verification/README.md) for isolated compatibility checks.

[![](https://jitpack.io/v/CodingAir/TradeSystem.svg)](https://jitpack.io/#CodingAir/TradeSystem)

# API
When working with the API of TradeSystem, please note the library relocation as stated below.

## Maven
```xml
<repository>
  <id>jitpack.io</id>
  <url>https://jitpack.io</url>
</repository>

<dependency>  
  <groupId>com.github.CodingAir</groupId>
  <artifactId>TradeSystem</artifactId>  
  <version>v2.6.2</version>  
</dependency>

<dependency>
    <groupId>com.github.CodingAir</groupId>
    <artifactId>CodingAPI</artifactId>
    <version>1.84</version>
    <scope>provided</scope>
</dependency>

<plugins>
    <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <version>3.2.3</version>
    
        <configuration>
            <relocations>
                <relocation>
                    <pattern>de.codingair.codingapi</pattern>
                    <shadedPattern>de.codingair.tradesystem.lib.codingapi</shadedPattern>
                </relocation>
            </relocations>
        </configuration>
    
        <executions>
            <execution>
                <phase>package</phase>
                <goals>
                    <goal>shade</goal>
                </goals>
            </execution>
        </executions>
    </plugin>
</plugins>
```

## Gradle

```gradle
plugins {
    id 'java'
    id 'com.github.johnrengelman.shadow' version "7.1.2"
}

repositories {
    ...
    maven { url 'https://jitpack.io' }
    ...
}

dependencies {
    ...
    compileOnly 'com.github.CodingAir:TradeSystem:2.5.3'
    compileOnly 'com.github.CodingAir:CodingAPI:1.79'
    ...
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

shadowJar {
    relocate 'de.codingair.codingapi', 'de.codingair.tradesystem.lib.codingapi'
}
```
