package uz.dukeengine.dungeon.run;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

/**
 * The saves folder: one file a slot, {@code <slot>.duke} — the autosave, and {@code slot1} … {@code slot3} once a
 * player can write them. Beside the engine's settings, in {@code ~/.duke-engine/saves/}.
 */
public final class Saves {

    private static final Logger LOG = Logger.getLogger(Saves.class.getName());

    public static final String AUTOSAVE = "autosave";
    public static final List<String> SLOTS = List.of(AUTOSAVE, "slot1", "slot2", "slot3");

    /** One readable save, its slot, and when it was written. */
    public record Saved(String slot, SaveGame game, FileTime written) {
    }

    private final Path folder;

    public Saves(Path folder) {
        this.folder = folder;
    }

    /** The player's own: beside the engine's {@code settings.properties}. */
    public static Saves ofThePlayer() {
        return new Saves(Path.of(System.getProperty("user.home"), ".duke-engine", "saves"));
    }

    /**
     * Write it over whatever the slot held, making the folder if there is none. Written beside and moved into place,
     * so a crash halfway leaves the last good save rather than half of a new one.
     */
    public void write(String slot, SaveGame game) throws IOException {
        Files.createDirectories(folder);
        var file = folder.resolve(slot + ".duke");
        var fresh = folder.resolve(slot + ".duke.new");
        Files.writeString(fresh, game.text());
        Files.move(fresh, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** The save in {@code slot} — an act's end, {@code act1} — or null when there is none that can be read. */
    public SaveGame read(String slot) {
        var file = folder.resolve(slot + ".duke");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return SaveGame.read(Files.readString(file), file.getFileName().toString());
        } catch (IOException | SaveGame.Refused e) {
            LOG.warning(() -> "the save " + slot + " cannot be read: " + e.getMessage());
            return null;
        }
    }

    /** Every save that can be read, newest first; one that cannot is left out, and the log says why. */
    public List<Saved> list() {
        var found = new ArrayList<Saved>();
        for (var slot : SLOTS) {
            var file = folder.resolve(slot + ".duke");
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try {
                found.add(new Saved(slot, SaveGame.read(Files.readString(file), file.getFileName().toString()),
                        Files.getLastModifiedTime(file)));
            } catch (IOException | SaveGame.Refused e) {
                LOG.warning(() -> "a save is left out: " + e.getMessage());
            }
        }
        found.sort(Comparator.comparing(Saved::written).reversed());
        return List.copyOf(found);
    }
}
