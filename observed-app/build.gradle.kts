plugins {
    id("conventions.spring")
    id("conventions.opentelemetry")
}

springBoot {
    mainClass.set("com.observeit.observedapp.ObservedAppApplicationKt")
}
