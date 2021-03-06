/******************************************************************************* 
 * Copyright (c) 2007-2009 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.vpe.editor.menu;

/**
 * @author yzhishko
 */

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.resource.ImageDescriptor;
import org.jboss.tools.vpe.editor.mozilla.MozillaEditor;
import org.jboss.tools.vpe.editor.template.IZoomEventManager;

public class ZoomActionMenuManager extends MenuManager {

	private static final String ZOOM_MENU = "Zoom"; //$NON-NLS-1$
	private static final String ZOOM_IN = "Zoom In\tCtrl++"; //$NON-NLS-1$
	private static final String ZOOM_OUT = "Zoom Out\tCtrl+-"; //$NON-NLS-1$
	private static final String RESET = "Reset\tCtrl+0"; //$NON-NLS-1$
	private IZoomEventManager manager;

	public ZoomActionMenuManager(IZoomEventManager manager) {
		super(ZOOM_MENU);
		this.manager = manager;
		add(new ZoomInAcion());
		add(new ZoomOutAction());
		add(new Separator());
		add(new ResetZoomViewAction());
	}

	private class ZoomInAcion extends Action {

		public ZoomInAcion() {
			setText(ZOOM_IN);
			setImageDescriptor(ImageDescriptor.createFromFile(
					MozillaEditor.class, VpeMenuUtil.ICON_MENU_ZOOM_IN));
		}

		@Override
		public void run() {
			manager.zoomIn();
		}

	}

	private class ZoomOutAction extends Action {

		public ZoomOutAction() {
			setText(ZOOM_OUT);
			setImageDescriptor(ImageDescriptor.createFromFile(
					MozillaEditor.class, VpeMenuUtil.ICON_MENU_ZOOM_OUT));
		}

		@Override
		public void run() {
			manager.zoomOut();
		}

	}

	private class ResetZoomViewAction extends Action {

		public ResetZoomViewAction() {
			setText(RESET);
		}

		@Override
		public void run() {
			manager.resetZoomView();
		}

	}

}
