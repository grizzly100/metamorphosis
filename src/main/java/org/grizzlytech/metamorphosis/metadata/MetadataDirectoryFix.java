package org.grizzlytech.metamorphosis.metadata;

import com.drew.metadata.mov.metadata.QuickTimeMetadataDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;

public class MetadataDirectoryFix {

    private static final Logger LOG = LoggerFactory.getLogger(MetadataDirectoryFix.class);

    public static void applyFixes() {
        fixQuickTime();
    }

    private static final boolean USE_FALLBACK_MAP = false;

    /**
     * Address missing metadata attributes referenced in older MOV files
     */
    private static void fixQuickTime() {
        try {
            // Obtain the _tagIntegerMap Field and object instance
            Field _tagIntegerMapField = QuickTimeMetadataDirectory.class.getDeclaredField("_tagIntegerMap");
            makeAccessible(_tagIntegerMapField);
            HashMap<String, Integer> _tagIntegerMap = (HashMap<String, Integer>) _tagIntegerMapField.get(null);

            // Add common tags referenced by older MOV files
            final int DEFAULT = -1;
            _tagIntegerMap.put("com.apple.quicktime.camera.identifier", DEFAULT);
            _tagIntegerMap.put("com.apple.quicktime.camera.framereadouttimeinmicroseconds", DEFAULT);
            _tagIntegerMap.put("com.apple.photos.captureMode", DEFAULT);

            if (USE_FALLBACK_MAP) {
                // Copy all entries into a fallback HashMap and define a default for all missing values
                HashMap<String, Integer> _tagIntegerFallbackMap = new FallbackHashMap<>((m, k) -> DEFAULT);
                _tagIntegerFallbackMap.putAll(_tagIntegerMap);

                // Swap the original map for the fallback
                _tagIntegerMapField.set(null, _tagIntegerFallbackMap);
            }
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            LOG.error("Unable to gain access to _tagIntegerMap", ex);
        }
    }

    /**
     * This will generate the following in Java 9+
     * WARNING: An illegal reflective access operation has occurred
     * WARNING: Illegal reflective access by MetadataDirectoryFix to field java.lang.reflect.Field.modifiers
     * WARNING: Please consider reporting this to the maintainers of MetadataDirectoryFix
     * WARNING: Use --illegal-access=warn to enable warnings of further illegal reflective access operations
     * WARNING: All illegal access operations will be denied in a future release
     *
     * @param field field to be replaced
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    protected static void makeAccessible(Field field)
            throws NoSuchFieldException, IllegalAccessException {
        // Make the field accessible
        field.setAccessible(true);
        // If we plan to use a fallback map, then the final modifier will need to be removed (if present)
        // NOTE: this must be removed BEFORE accessing the field, otherwise it does not work.
        if (USE_FALLBACK_MAP && Modifier.isFinal(field.getModifiers())) {
            LOG.info("Attempting to remove 'final' modifier from field [{}]", field.getName());
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
            LOG.info("Result -> Modifier.isFinal = [{}]", Modifier.isFinal(field.getModifiers()));
        }
    }
}
