package android.net;

import android.os.Parcel;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class TestUri extends Uri {
    private final String raw;

    public TestUri(String raw) {
        this.raw = raw;
    }

    @Override
    public boolean isHierarchical() {
        return true;
    }

    @Override
    public boolean isRelative() {
        return false;
    }

    @Override
    public String getScheme() {
        int index = raw.indexOf(':');
        return index >= 0 ? raw.substring(0, index) : null;
    }

    @Override
    public String getSchemeSpecificPart() {
        int index = raw.indexOf(':');
        return index >= 0 ? raw.substring(index + 1) : raw;
    }

    @Override
    public String getEncodedSchemeSpecificPart() {
        return getSchemeSpecificPart();
    }

    @Override
    public String getAuthority() {
        int start = raw.indexOf("//");
        if (start < 0) {
            return null;
        }
        int authorityStart = start + 2;
        int end = raw.indexOf('/', authorityStart);
        if (end < 0) {
            return raw.substring(authorityStart);
        }
        return raw.substring(authorityStart, end);
    }

    @Override
    public String getEncodedAuthority() {
        return getAuthority();
    }

    @Override
    public String getUserInfo() {
        return null;
    }

    @Override
    public String getEncodedUserInfo() {
        return null;
    }

    @Override
    public String getHost() {
        return getAuthority();
    }

    @Override
    public int getPort() {
        return -1;
    }

    @Override
    public String getPath() {
        int start = raw.indexOf("//");
        if (start < 0) {
            return "";
        }
        int authorityStart = start + 2;
        int pathStart = raw.indexOf('/', authorityStart);
        if (pathStart < 0) {
            return "";
        }
        return raw.substring(pathStart);
    }

    @Override
    public String getEncodedPath() {
        return getPath();
    }

    @Override
    public String getQuery() {
        return null;
    }

    @Override
    public String getEncodedQuery() {
        return null;
    }

    @Override
    public String getFragment() {
        return null;
    }

    @Override
    public String getEncodedFragment() {
        return null;
    }

    @Override
    public List<String> getPathSegments() {
        String path = getPath();
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return Collections.emptyList();
        }
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        return Arrays.asList(normalized.split("/"));
    }

    @Override
    public String getLastPathSegment() {
        List<String> segments = getPathSegments();
        return segments.isEmpty() ? null : segments.get(segments.size() - 1);
    }

    @Override
    public String toString() {
        return raw;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TestUri)) {
            return false;
        }
        TestUri that = (TestUri) other;
        return raw.equals(that.raw);
    }

    @Override
    public int hashCode() {
        return raw.hashCode();
    }

    @Override
    public Builder buildUpon() {
        throw new UnsupportedOperationException("Not needed in unit tests");
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        throw new UnsupportedOperationException("Not needed in unit tests");
    }
}
