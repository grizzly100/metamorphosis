package org.grizzlytech.metamorphosis.metadata;

import com.drew.metadata.mov.metadata.QuickTimeMetadataDirectory;
import org.grizzlytech.metamorphosis.util.UnsafeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.function.BiFunction;

public class MetadataDirectoryFix {

    private static final Logger LOG = LoggerFactory.getLogger(MetadataDirectoryFix.class);

    private static final boolean USE_FALLBACK_MAP = true;

    private static final int DEFAULT_TAG_VALUE = -1;

    public static void applyFixes() {
        fixQuickTime();
    }

    /**
     * Edit this method to add additional missing tags
     *
     * @param _tagIntegerMap the QT tag map to be added to
     */
    protected static void addTags(HashMap<String, Integer> _tagIntegerMap) {
        // Add common tags referenced by older MOV files
        _tagIntegerMap.put("com.apple.quicktime.camera.identifier", DEFAULT_TAG_VALUE);
        _tagIntegerMap.put("com.apple.quicktime.camera.framereadouttimeinmicroseconds", DEFAULT_TAG_VALUE);
        _tagIntegerMap.put("com.apple.photos.captureMode", DEFAULT_TAG_VALUE);
        _tagIntegerMap.put("com.apple.quicktime.location.accuracy.horizontal", DEFAULT_TAG_VALUE);
    }


    /**
     * Address missing metadata attributes referenced in older MOV files
     */
    protected static void fixQuickTime() {

        try {
            // Obtain the _tagIntegerMap Field and HashMap object instance
            Field _tagIntegerMapField = getTagIntegerMapField();
            HashMap<String, Integer> _tagIntegerMap = getTagIntegerMap(_tagIntegerMapField);

            // Add common tags referenced by files but missing from library
            addTags(_tagIntegerMap);

            if (USE_FALLBACK_MAP) {
                // Copy all entries into a HashMap and define a default for all missing values
                HashMap<String, Integer> _tagIntegerFallbackMap = new FallbackHashMap<>(DEFAULT_TAG_VALUE);
                _tagIntegerFallbackMap.putAll(_tagIntegerMap);

                // Swap the original map for the fallback
                UnsafeUtil.staticFieldSwapObject(_tagIntegerMapField, _tagIntegerFallbackMap);
            }
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            LOG.error("Unable to gain access to _tagIntegerMap", ex);
        }
    }

    protected static Field getTagIntegerMapField() throws NoSuchFieldException {
        Field _tagIntegerMapField = QuickTimeMetadataDirectory.class.getDeclaredField("_tagIntegerMap");
        LOG.info("Located _tagIntegerMapField: " + _tagIntegerMapField);
        // Make the field accessible so its value can be retrieved
        _tagIntegerMapField.setAccessible(true);
        return _tagIntegerMapField;
    }

    protected static HashMap<String, Integer> getTagIntegerMap(Field _tagIntegerMapField)
            throws IllegalAccessException {
        return (HashMap<String, Integer>) _tagIntegerMapField.get(null);
    }
}
