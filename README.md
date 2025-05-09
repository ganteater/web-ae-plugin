# Web Anteater Plugin

[![Anteater](https://img.shields.io/maven-central/v/com.ganteater/web-ae-plugin.svg)](https://central.sonatype.com/artifact/com.ganteater/web-ae-plugin)

## Introdaction

```platuml
@startuml
class Web [[java:com.ganteater.ae.processor.Web]] {
	+Web(aParent: Processor)
	+init(aParent: Processor, action: Node): void
	+runCommandRunIfFirst(action: Node): void
	+runCommandPage(action: Node): void
	+runCommandText(action: Node): void
	+runCommandCloseTab(action: Node): void
	+runCommandClick(action: Node): void
	+runCommandGetText(action: Node): void
	+runCommandGetUrl(action: Node): void
	+runCommandFrame(action: Node): void
	+runCommandElementExists(action: Node): void
	+runCommandElementNotExists(action: Node): void
	+runCommandCheckPage(action: Node): void
	+runCommandTitle(action: Node): void
	+runCommandRefresh(action: Node): void
	+runCommandCloseDriver(action: Node): void
	+runCommandDeleteCookie(action: Node): void
	+runCommandCookieReport(action: Node): void
	+complete(success: boolean): void
}
class Web {
}
Web --> "1" Web : webParrentProcessor
class Web {
}
class BaseProcessor {
}
BaseProcessor <|-- Web
@enduml

## Usage

```xml
<dependency>
    <groupId>com.ganteater</groupId>
    <artifactId>web-ae-plugin</artifactId>
    <version>1.2.0</version>
</dependency>
```
