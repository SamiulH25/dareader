package android.net;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal dareader-owned Uri stub delegating parsing to {@link URI}
 * (opaque reference type for Page.uri, plus query/host/path accessors
 * and a small {@link Builder} for extensions building request URLs).
 */
public abstract class Uri {
    @Override
    public abstract String toString();

    public abstract String getHost();

    public abstract String getPath();

    public abstract String getQueryParameter(String key);

    public abstract Builder buildUpon();

    public static Uri parse(String uriString) {
        final String s = uriString;
        URI parsed = null;
        try {
            parsed = new URI(s);
        } catch (Exception ignored) {
        }
        final URI u = parsed;
        return new Uri() {
            @Override
            public String toString() {
                return s;
            }

            @Override
            public String getHost() {
                return u == null ? null : u.getHost();
            }

            @Override
            public String getPath() {
                return u == null ? null : u.getPath();
            }

            @Override
            public String getQueryParameter(String key) {
                return queryParameter(s, key);
            }

            @Override
            public Builder buildUpon() {
                return new Builder(s);
            }
        };
    }

    static String queryParameter(String uriString, String key) {
        int q = uriString.indexOf('?');
        if (q < 0) return null;
        int end = uriString.indexOf('#', q);
        String query = end < 0 ? uriString.substring(q + 1) : uriString.substring(q + 1, end);
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String name = eq < 0 ? pair : pair.substring(0, eq);
            if (decode(name).equals(key)) {
                return eq < 0 ? "" : decode(pair.substring(eq + 1));
            }
        }
        return null;
    }

    static String decode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return s;
        }
    }

    static String encodeComponent(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            return s;
        }
    }

    public static String encode(String s) {
        StringBuilder out = new StringBuilder();
        for (char c : s.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || "-_.~".indexOf(c) >= 0) {
                out.append(c);
            } else {
                out.append(String.format("%%%02X", (int) c));
            }
        }
        return out.toString();
    }

    /** Minimal builder: base URL plus appended query parameters. */
    public static final class Builder {
        private final String base;
        private final List<String[]> params = new ArrayList<>();

        public Builder() {
            this("");
        }

        Builder(String base) {
            this.base = base;
        }

        public Builder appendQueryParameter(String key, String value) {
            params.add(new String[] {key, value});
            return this;
        }

        public Uri build() {
            if (params.isEmpty()) return Uri.parse(base);
            StringBuilder sb = new StringBuilder(base);
            sb.append(base.contains("?") ? '&' : '?');
            for (int i = 0; i < params.size(); i++) {
                if (i > 0) sb.append('&');
                sb.append(encodeComponent(params.get(i)[0]));
                sb.append('=');
                sb.append(encodeComponent(params.get(i)[1]));
            }
            return Uri.parse(sb.toString());
        }

        @Override
        public String toString() {
            return build().toString();
        }
    }
}
