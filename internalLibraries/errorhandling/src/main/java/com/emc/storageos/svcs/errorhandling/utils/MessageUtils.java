/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.svcs.errorhandling.utils;

import static org.apache.commons.lang.StringUtils.isBlank;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import javax.lang.model.element.Element;

import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;

public class MessageUtils {
    private MessageUtils() {
    }

    public static ResourceBundle bundleForClass(final Class<?> clazz) throws IllegalStateException {
        return bundleForClass(clazz, Locale.ENGLISH);
    }

    public static ResourceBundle bundleForClass(final Class<?> clazz, final Locale locale)
            throws IllegalStateException, MissingResourceException {
        final String detailBase = bundleNameForClass(clazz);

        return bundleForName(detailBase, locale);
    }

    public static ResourceBundle bundleForName(final String name) throws MissingResourceException {
        return bundleForName(name, Locale.ENGLISH);
    }

    public static ResourceBundle bundleForName(final String name, final Locale locale)
            throws MissingResourceException {
        return ResourceBundle.getBundle(name, locale);
    }

    public static String bundleNameForClass(final Class<?> clazz) throws IllegalStateException {
        final MessageBundle bundle = clazz.getAnnotation(MessageBundle.class);

        if (bundle == null) {
            throw new IllegalStateException("The class must be annotated with @MessageBundle");
        }

        final String detailBase = isBlank(bundle.value()) ? clazz.getCanonicalName() : bundle
                .value();
        return detailBase;
    }

    public static String bundleNameForElement(final Element element) throws IllegalStateException {
        final MessageBundle bundle = element.getAnnotation(MessageBundle.class);

        if (bundle == null) {
            throw new IllegalStateException("The class must be annotated with @MessageBundle");
        }
        final String detailBase = isBlank(bundle.value()) ? element.asType().toString() : bundle
                .value();
        return detailBase;
    }

    public static ResourceBundle bundleForElement(final Element element)
            throws IllegalStateException {
        return bundleForElement(element, Locale.ENGLISH);
    }

    public static ResourceBundle bundleForElement(final Element element, final Locale locale)
            throws IllegalStateException, MissingResourceException {
        final String detailBase = bundleNameForElement(element);
        return bundleForName(detailBase, locale);
    }
}
