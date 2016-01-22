package com.dianping.pigeon.governor.util;

import org.apache.commons.lang.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by chenchongze on 16/1/22.
 */
public class CommonUtils {

    public static String normalizeHosts(String hosts) {
        return StringUtils.join(new HashSet<String>(Arrays.asList(hosts.split(","))), ",");
    }
}
