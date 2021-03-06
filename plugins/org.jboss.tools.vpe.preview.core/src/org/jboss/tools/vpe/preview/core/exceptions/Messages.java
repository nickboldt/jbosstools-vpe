package org.jboss.tools.vpe.preview.core.exceptions;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
	private static final String BUNDLE_NAME = "org.jboss.tools.vpe.preview.core.exceptions.messages"; //$NON-NLS-1$
	public static String NO_ENGINE_ERROR;
	public static String MOZILLA_LOADING_ERROR;
	public static String MOZILLA_EXPERIMENTAL_SUPPORT;
	public static String MOZILLA_LOADING_ERROR_LINK_TEXT;
	public static String MOZILLA_LOADING_ERROR_LINK;
	public static String GTK3_IS_NOT_SUPPORTED;
	public static String CURRENT_PLATFORM_IS_NOT_SUPPORTED;
	
	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {
	}
}
