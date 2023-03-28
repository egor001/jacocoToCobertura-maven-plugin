package ru.siblion.lab;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jdom2.JDOMException;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Converts the jacoco file format to cobertura format to display test coverage in gitlab.
 */
@Mojo(name = "convert")
public class ConverterMojo extends AbstractMojo {

    /**
     * Gives access to the Maven project information.
     */
    @Parameter(property = "project", readonly = true)
    private MavenProject project;

    /**
     * Path to jacoco format file.
     */
    @Parameter(property = "source", defaultValue = "/target/site/jacoco/jacoco.xml")
    String source;

    /**
     * Path, where the cobertura format file is to be written.
     */
    @Parameter(property = "result", defaultValue = "/target/site/cobertura/cobertura.xml")
    String result;

    /**
     * List of paths to modules of the project.
     */
    @Parameter(property = "pathsToProject", defaultValue = "/src/main/java/")
    List<String> pathsToProject;

    @Override
    public void execute() {
        try {
            Converter.convert(Path.of(source), Path.of(result), pathsToProject);
        } catch (IOException | XMLStreamException | JDOMException e) {
            throw new RuntimeException(e);
        }
    }
}