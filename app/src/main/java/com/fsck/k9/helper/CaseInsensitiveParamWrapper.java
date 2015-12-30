package com.fsck.k9.helper;

import android.net.Uri;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by Alexander S. Sidorov on 12/30/15.
 */
public class CaseInsensitiveParamWrapper
{
    private final Uri uri;
    private Set<String> mParamNames;

    public CaseInsensitiveParamWrapper(Uri uri) {
        this.uri = uri;
    }

    public List<String> getQueryParameters(String key) {
        final List<String> params = new ArrayList<String>();
        for (String paramName : uri.getQueryParameterNames()) {
            if (paramName.equalsIgnoreCase(key)) {
                params.addAll(uri.getQueryParameters(paramName));
            }
        }
        return params;
    }
}
