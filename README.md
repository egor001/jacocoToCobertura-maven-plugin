# JacocoToCobertura maven plugin

Плагин для конвертации файла формата jacoco в формат cobertura для визуализации покрытия тестами проекта в Gitlab

## Минимальные требования

* Java 17 и выше
* Jacoco plugin

## Переменные

* **source** - путь к файлу формата jacoco (дефолтное значение: `/target/site/jacoco/jacoco.xml`).
* **result** - путь, куда файл `cobertura.xml` будет размещен после работы плагина (дефолтное
  значение: `/target/site/cobertura/cobertura.xml`).
* **pathsToProject** - список путей к классам относительно корня проекта (дефолтное значение: `/src/main/java/`).

## Использование

### Подготовка

Необходимо чтобы в проекте был Jacoco plugin. Для этого в `pom.xml` необходимо подключить его. Например:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.8</version>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```
**Важно!**   
Необходимо наличие файла `jacoco.xml` перед запуском **jacocoToCobertura plugin**. Так как **jacocoToCobertura plugin** 
обрабатывает этот файл.

### Использование в Gitlab

Задача данного плагина - графическое отображение покрытия тестами классов и методов в GitLab. Поэтому логично
использование данного плагина в файле
`.gitlab-ci.yml`. Пример файла `.gitlab-ci.yml` показывает использование данного плагина без дополнительных изменений
в `pom.xml` Вашего проекта.


```yaml
variables:
  MAVEN_OPTS: "-Dmaven.repo.local=$CI_PROJECT_DIR/.m2/repository"
  MAVEN_CLI_OPTS: "-ntp -s .m2/settings.xml --batch-mode"

stages:
  - check
  - test
  - visualize

default:
  image: "${CI_DEPENDENCY_PROXY_GROUP_IMAGE_PREFIX}/eclipse-temurin:17.0.3_7-jdk-alpine"
  before_script:
    - chmod +x ./mvnw

mvn-check:
  stage: check
  script:
    - "./mvnw $MAVEN_CLI_OPTS versions:display-property-updates versions:display-parent-updates -DallowSnapshots=true"
  only:
    - merge_requests
    - main

mvn-test:
  stage: test
  script:
    - "./mvnw $MAVEN_CLI_OPTS clean test"
  artifacts:
    paths:
      - target/site/jacoco/jacoco.xml

  only:
    - merge_requests
    - main

coverage:
  needs: ["mvn-test"]
  stage: quality_gate
  script:
    - "./mvnw -U $MAVEN_CLI_OPTS dependency:get -Dartifact=ru.siblion.lab:jacocoToCobertura-maven-plugin:0.0.1"
    - "./mvnw $MAVEN_CLI_OPTS ru.siblion.lab:jacocoToCobertura-maven-plugin:0.0.1:convert
        -Dsource=\"${CI_PROJECT_NAME}-test/target/site/jacoco-aggregate/jacoco.xml\"
        -Dresult=\"${CI_PROJECT_NAME}-test/target/site/cobertura/cobertura.xml\"
        -DpathsToProject=\"${CI_PROJECT_DIR}/${CI_PROJECT_NAME}/src/main/java/\",\"${CI_PROJECT_DIR}/${CI_PROJECT_NAME}-api/src/main/java/\""
  allow_failure: true
  coverage: "/Total.*?([0-9]{1,3})%/"
  artifacts:
    reports:
      coverage_report:
        coverage_format: cobertura
        path: ${CI_PROJECT_NAME}-test/target/site/cobertura/cobertura.xml
  only:
    - merge_requests
```

Данный пример рассмотрен для мультимодульного проекта с файловой структурой:
```text
pet/src/main/java
pet-api/src/main/java
pet-test/src/main/java
```
### Подключение в проект для локального использования

Для подключения плагина в проект необходимо в файл **pom.xml** вставить:

```xml

<project>
    ...
    <build>
        <!-- To define the plugin version in your parent POM -->
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>ru.siblion.lab</groupId>
                    <artifactId>jacocoToCobertura-maven-plugin</artifactId>
                    <version>0.0.1</version>
                </plugin>
                ...
            </plugins>
        </pluginManagement>
        <!-- To use the plugin goals in your POM or parent POM -->
        <plugins>
            <plugin>
                <groupId>ru.siblion.lab</groupId>
                <artifactId>jacocoToCobertura-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <goals>
                            <goal>convert</goal>
                        </goals>
                    </execution>
                </executions>
                <configuration>
                    <source>path to jacoco.xml</source>
                    <result>path to cobertura.xml</result>
                    <pathsToProject>path to classes of project</pathsToProject>
                </configuration>
            </plugin>
            ...
        </plugins>
    </build>
    ...
</project>
```
### Тесты
В jacocoToCobertura-maven-plugin есть модульные и интеграционные тесты. 
## Авторы

- Дмитриев Егор
- Митрофанов Сергей

## Ссылки

- [Maven Central]()
- [Siblion](https://www.siblion.ru/)
