package ru.siblion.lab;

import lombok.extern.slf4j.Slf4j;
import org.jdom2.JDOMException;
import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
class ConverterTest {
    @Test
    void converterTest() throws XMLStreamException, IOException, JDOMException {
        Converter.convert(Path.of("src/test/resources/__files/testFiles/jacoco.xml"),
                Path.of("target/testDir/cobertura.xml"),
                List.of("/src/main/java/"));

        var file = new File("target/testDir/cobertura.xml");

        assertAll(
                () -> assertEquals(Boolean.TRUE, file.exists()),
                () -> assertEquals(3215, Files.readAllBytes(file.toPath()).length)
        );
    }
}
