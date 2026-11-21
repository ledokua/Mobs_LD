package net.ledok.mobs_ld.mob.behaviour;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.ledok.mobs_ld.mob.MobDeclaration;
import net.ledok.mobs_ld.mob.damage.AttackProfile;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** The starter files in docs/examples/ must stay valid as the format grows (they are copied as templates). */
class ExamplesTest {
    private static final Path EXAMPLES = Path.of(System.getProperty("mobs_ld.projectDir", "."), "docs", "examples");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static JsonElement read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file));
    }

    private static List<Path> jsonIn(String... dirs) throws IOException {
        try (Stream<Path> files = Files.list(EXAMPLES.resolve(Path.of("", dirs)))) {
            return files.filter(file -> file.toString().endsWith(".json")).sorted().toList();
        }
    }

    @Test
    void behavioursParse() throws IOException {
        List<Path> files = Stream.concat(jsonIn("vanilla_host").stream(),
                jsonIn("custom_boss", "mob_behaviour").stream()).toList();
        assertFalse(files.isEmpty());
        for (Path file : files) {
            MobBehaviour.CODEC.parse(JsonOps.INSTANCE, read(file)).getOrThrow(error -> new AssertionError(file + ": " + error));
        }
    }

    @Test
    void entityDeclarationsParse() throws IOException {
        for (Path file : jsonIn("custom_boss", "entities")) {
            String name = file.getFileName().toString().replace(".json", "");
            MobDeclaration.codec(ResourceLocation.fromNamespaceAndPath("mobs_ld", name))
                    .parse(JsonOps.INSTANCE, read(file)).getOrThrow(error -> new AssertionError(file + ": " + error));
        }
    }

    @Test
    void attackProfilesParse() throws IOException {
        for (Path file : jsonIn("attack_profile")) {
            AttackProfile.CODEC.parse(JsonOps.INSTANCE, read(file)).getOrThrow(error -> new AssertionError(file + ": " + error));
        }
    }
}
