package org.uritemplate;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@Warmup(iterations = 5)
@Measurement(iterations = 10)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(2)
public class BenchmarkUriTemplate {

    @Param({"OO", "New", "Micronaut"})
    private String name;
    private UriTemplate uriTemplate;

    private String template;
    private Map<String, Object> substitutions;

    @Setup
    public void setup() {
        template = "/search.{format}{?q,geocode,lang,locale,page,result_type}";
        substitutions = new HashMap<>();
        substitutions.put("format", "json");
        substitutions.put("q", "URI Templates");
        substitutions.put("geocode", List.of("37.76","-122.427"));
        substitutions.put("lang", "en");
        substitutions.put("page", "5");

        if (name.equals("OO")) {
            uriTemplate = new StdUriTemplateOO();
        } else  if (name.equals("New")) {
            uriTemplate = new StdUriTemplateNew();
        } else if (name.equals("Micronaut")) {
            uriTemplate = new UriTemplateMicronaut(null);
        } else {
            throw new IllegalArgumentException("No implementation found for " + name);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void benchmark(Blackhole bh) {
        bh.consume(uriTemplate.benchmark(template, substitutions));
    }

}
