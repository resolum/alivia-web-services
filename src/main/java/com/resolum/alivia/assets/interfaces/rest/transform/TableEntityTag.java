package com.resolum.alivia.assets.interfaces.rest.transform;

/**
 * The entity tag of the table of devices of an installation: {@code "<installationId>.<version>"}.
 *
 * <p>The version alone is not enough. Versions of different installations cannot be compared: an edge service that is
 * taken out of one household and placed in another would otherwise be told it is up to date when both tables happen to
 * be at the same number. With the installation inside the tag, a tag from another installation never matches.</p>
 *
 * <p>An edge that sends a tag of this installation with a version <em>higher</em> than the current one is ahead of the
 * server, which only happens when the database went back in time (a restore from an older backup): see
 * {@link Relation#AHEAD}.</p>
 */
public final class TableEntityTag {

    /** How the tags that an edge sent relate to the current table of its installation. */
    public enum Relation {
        /** One of the tags is exactly the current table (or the header is {@code *}): the edge is up to date. */
        UP_TO_DATE,
        /** None matches, and one is from this installation with a version higher than the current: the edge is ahead. */
        AHEAD,
        /** None matches and none is ahead: older, from another installation, malformed or absent. The edge gets the table. */
        DIFFERENT
    }

    private TableEntityTag() {
    }

    /** The tag of the table of an installation at a version, quoted as it goes in the {@code ETag} header. */
    public static String of(long installationId, long version) {
        return "\"" + installationId + "." + version + "\"";
    }

    /**
     * Compares the {@code If-None-Match} header with the current table. Weak tags ({@code W/}) and lists are accepted;
     * anything that does not have the form {@code "<installation>.<version>"} (the old version-only form included)
     * simply does not match, so the edge gets the table.
     */
    public static Relation relation(String ifNoneMatch, long installationId, long currentVersion) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return Relation.DIFFERENT;
        }
        var ahead = false;
        for (var raw : ifNoneMatch.split(",")) {
            var tag = raw.strip();
            if (tag.equals("*")) {
                return Relation.UP_TO_DATE;
            }
            if (tag.startsWith("W/")) {
                tag = tag.substring(2);
            }
            if (tag.length() < 2 || !tag.startsWith("\"") || !tag.endsWith("\"")) {
                continue;
            }
            var parts = tag.substring(1, tag.length() - 1).split("\\.", -1);
            if (parts.length != 2) {
                continue;
            }
            var tagInstallation = digits(parts[0]);
            var tagVersion = digits(parts[1]);
            if (tagInstallation == null || tagVersion == null || tagInstallation != installationId) {
                continue;
            }
            if (tagVersion == currentVersion) {
                return Relation.UP_TO_DATE;
            }
            if (tagVersion > currentVersion) {
                ahead = true;
            }
        }
        return ahead ? Relation.AHEAD : Relation.DIFFERENT;
    }

    /** A non-negative number written with digits only (no sign, no spaces, no leading junk), or {@code null}. */
    private static Long digits(String text) {
        // Strict like the tag itself: "012" is not the tag that was sent for 12.
        if (text.isEmpty() || text.length() > 18 || (text.length() > 1 && text.charAt(0) == '0')) {
            return null;
        }
        for (var i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i)) || text.charAt(i) > '9') {
                return null;
            }
        }
        return Long.parseLong(text);
    }
}
