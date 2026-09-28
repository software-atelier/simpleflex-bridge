package ch.software_atelier.simpleflex.bridge;

/** Per-directory options from the .list file. Unknown options are ignored for forward compatibility. */
record ListConfig(boolean hidden, boolean up) {
    static ListConfig parse(String content) {
        boolean hidden = false;
        boolean up = false;
        for (String line : content.split("\\R")) {
            String option = line.trim();
            if (option.isEmpty() || option.startsWith("#")) continue;
            int equals = option.indexOf('=');
            if (equals < 0) continue;
            String key = option.substring(0, equals).trim();
            String value = option.substring(equals + 1).trim();
            if (!value.equals("true") && !value.equals("false")) continue;
            if (key.equals("hidden")) hidden = Boolean.parseBoolean(value);
            else if (key.equals("up")) up = Boolean.parseBoolean(value);
        }
        return new ListConfig(hidden, up);
    }
}
