/*******************************************************************************
 * Copyright (c) 2007 Exadel, Inc. and Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Exadel, Inc. and Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.vpe.editor;

import static org.jboss.tools.vpe.xulrunner.util.XPCOM.queryInterface;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IStorage;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.wst.sse.core.internal.provisional.INodeAdapter;
import org.eclipse.wst.sse.core.internal.provisional.INodeNotifier;
import org.eclipse.wst.xml.core.internal.document.ElementImpl;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMElement;
import org.jboss.tools.common.resref.core.ResourceReference;
import org.jboss.tools.jst.web.ui.internal.editor.preferences.IVpePreferencesPage;
import org.jboss.tools.jst.web.ui.WebUiPlugin;
import org.jboss.tools.vpe.VpePlugin;
import org.jboss.tools.vpe.editor.context.VpePageContext;
import org.jboss.tools.vpe.editor.mapping.VpeDomMapping;
import org.jboss.tools.vpe.editor.mapping.VpeElementData;
import org.jboss.tools.vpe.editor.mapping.VpeElementMapping;
import org.jboss.tools.vpe.editor.mapping.VpeNodeMapping;
import org.jboss.tools.vpe.editor.mozilla.MozillaEditor;
import org.jboss.tools.vpe.editor.proxy.VpeProxyUtil;
import org.jboss.tools.vpe.editor.template.VpeChildrenInfo;
import org.jboss.tools.vpe.editor.template.VpeCreationData;
import org.jboss.tools.vpe.editor.template.VpeCreatorUtil;
import org.jboss.tools.vpe.editor.template.VpeDefaultPseudoContentCreator;
import org.jboss.tools.vpe.editor.template.VpeHtmlTemplate;
import org.jboss.tools.vpe.editor.template.VpeTagDescription;
import org.jboss.tools.vpe.editor.template.VpeTemplate;
import org.jboss.tools.vpe.editor.template.VpeTemplateManager;
import org.jboss.tools.vpe.editor.template.VpeTemplateSafeWrapper;
import org.jboss.tools.vpe.editor.template.VpeToggableTemplate;
import org.jboss.tools.vpe.editor.template.expression.VpeExpressionException;
import org.jboss.tools.vpe.editor.util.Docbook;
import org.jboss.tools.vpe.editor.util.FaceletUtil;
import org.jboss.tools.vpe.editor.util.HTML;
import org.jboss.tools.vpe.editor.util.TextUtil;
import org.jboss.tools.vpe.editor.util.VpeStyleUtil;
import org.jboss.tools.vpe.editor.util.XmlUtil;
import org.jboss.tools.vpe.resref.core.CSSReferenceList;
import org.jboss.tools.vpe.xulrunner.editor.XulRunnerEditor;
import org.mozilla.interfaces.nsIDOMAttr;
import org.mozilla.interfaces.nsIDOMDocument;
import org.mozilla.interfaces.nsIDOMElement;
import org.mozilla.interfaces.nsIDOMNamedNodeMap;
import org.mozilla.interfaces.nsIDOMNode;
import org.mozilla.interfaces.nsIDOMNodeList;
import org.mozilla.interfaces.nsIDOMText;
import org.mozilla.xpcom.XPCOMException;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class VpeVisualDomBuilder extends VpeDomBuilder {
    public static final String VPE_USER_TOGGLE_ID = "vpe-user-toggle-id"; //$NON-NLS-1$
	public static final String VPE_USER_TOGGLE_LOOKUP_PARENT = "vpe-user-toggle-lookup-parent"; //$NON-NLS-1$
 	/*
 	 * https://jira.jboss.org/jira/browse/JBIDE-3373
 	 * Attribute that specifies the place where JSF facet should be rendered.
 	 */
 	public static final String VPE_FACET = "VPE-FACET"; //$NON-NLS-1$
	
	private static final String PSEUDO_ELEMENT_ATTR = "vpe:pseudo-element"; //$NON-NLS-1$
	private static final String MOZ_ANONCLASS_ATTR = "_MOZ_ANONCLASS"; //$NON-NLS-1$
	private static final String INCLUDE_ELEMENT_ATTR = "vpe:include-element"; //$NON-NLS-1$
	private static String DOTTED_BORDER = "border: 1px dotted #FF6600; padding: 5px;"; //$NON-NLS-1$
	private static final String CSS_STYLE_FOR_BORDER_FOR_UNKNOWN_TAGS = ";border: 1px solid green;"; //$NON-NLS-1$

	private MozillaEditor visualEditor;
	private XulRunnerEditor xulRunnerEditor;
	private VpePageContext pageContext;
	private List<VpeIncludeInfo> includeStack;

	private static final String ATTR_VPE = "vpe"; //$NON-NLS-1$
	private static final String ATTR_VPE_INLINE_LINK_VALUE = "inlinelink"; //$NON-NLS-1$

	private static final String ATTR_REL_STYLESHEET_VALUE = "stylesheet"; //$NON-NLS-1$

	private static final String YES_STRING = "yes"; //$NON-NLS-1$
	private static final String EMPTY_STRING = ""; //$NON-NLS-1$

	static private HashSet<String> unborderedSourceNodes = new HashSet<String>();
	static {
		unborderedSourceNodes.add(HTML.TAG_HTML);
		unborderedSourceNodes.add(HTML.TAG_HEAD);
		unborderedSourceNodes.add(HTML.TAG_BODY);
	}

	static private HashSet<String> unborderedVisualNodes = new HashSet<String>();
	static {
		unborderedVisualNodes.add(HTML.TAG_TBODY);
		unborderedVisualNodes.add(HTML.TAG_THEAD);
		unborderedVisualNodes.add(HTML.TAG_TR);
		unborderedVisualNodes.add(HTML.TAG_TD);
		unborderedVisualNodes.add(HTML.TAG_COL);
		unborderedVisualNodes.add(HTML.TAG_COLS);
		unborderedVisualNodes.add(HTML.TAG_COLGROUP);
		unborderedVisualNodes.add(HTML.TAG_LI);
		unborderedVisualNodes.add(HTML.TAG_BR);
	}

	private Map<IStorage, Document> includeDocuments = new HashMap<IStorage, Document>();
	private boolean showInvisibleTags;
	private boolean showBorderForUnknownTags;
	public static final List<nsIDOMNode> EMPTY_SELECTION = Collections.unmodifiableList(new ArrayList<nsIDOMNode>(0));

	public VpeVisualDomBuilder(VpeDomMapping domMapping, INodeAdapter sorceAdapter,
			MozillaEditor visualEditor, VpePageContext pageContext) {

		super(domMapping, sorceAdapter);
		this.visualEditor = visualEditor;
		xulRunnerEditor = visualEditor.getXulRunnerEditor();
		this.pageContext = pageContext;
		this.showInvisibleTags = WebUiPlugin.getDefault().getPreferenceStore().getBoolean(
				IVpePreferencesPage.SHOW_NON_VISUAL_TAGS);
		this.showBorderForUnknownTags = WebUiPlugin.getDefault().getPreferenceStore().getBoolean(
				IVpePreferencesPage.SHOW_BORDER_FOR_UNKNOWN_TAGS);
	}

	public void buildDom(Document sourceDocument) {
		nsIDOMNodeList children = getContentArea().getChildNodes();
		long len = children.getLength();
		for (long i = len - 1; i >= 0; i--) {
			getContentArea().removeChild(children.item(i));
		}
		final VpeSourceDomBuilder sourceBuilder = pageContext.getSourceBuilder();
		IDocument document = sourceBuilder.getStructuredTextViewer()
				.getDocument();
		if (document == null) {
			return;
		}
		includeStack = new ArrayList<VpeIncludeInfo>();
		IEditorInput input = pageContext.getEditPart().getEditorInput();
		if (input instanceof IFileEditorInput) {
			IFile file = ((IFileEditorInput) input).getFile();
			if (file != null) {
				pushIncludeStack(new VpeIncludeInfo(null, file, sourceBuilder.getSourceDocument()));
			}
		}

		pageContext.refreshConnector();
		pageContext.refreshResReferences();
		
		// FIXED FOR JBIDE-3799 by sdzmitrovich
		// it code is not necessary because addExternalLinks() does the same but
		// better
		// pageContext.installIncludeElements();
		
		refreshExternalLinks();
		
		/*
		 * https://jira.jboss.org/jira/browse/JBIDE-4398
		 * Additional check for facelet's taglibs should be added
		 * to distinguish it from custom tags and pages without facelets support.
		 */
		Element root = FaceletUtil.findComponentElement(sourceDocument.getDocumentElement());
		if ((root != null)
				&& (FaceletUtil.isFacelet(root,
						XmlUtil.getTaglibsForNode(root, pageContext)))) {
				addNode(root, null, getContentArea());
		} else {
			addNode(sourceDocument, null, getContentArea());
		}
		/*
		 * Fixes http://jira.jboss.com/jira/browse/JBIDE-2126. To provide
		 * appropriate context menu functionality visual content area should be
		 * mapped in any case.
		 */
		registerNodes(new VpeNodeMapping(sourceDocument, getContentArea()));
	}

	public void rebuildDom(Document sourceDocument) {
		// clearIncludeDocuments();
		cleanHead();
		domMapping.clear(getContentArea());
		super.dispose();

		pageContext.clearAll();
		pageContext.resetElService();
		// FIXED FOR JBIDE-3799 by sdzmitrovich, moved calling of this method to buid dom 
		// refreshExternalLinks();
		pageContext.getBundle().refreshRegisteredBundles();
		
		//Next path was moved to buildDom method
		//to avoid <br> in a visual DOM before editor browser
		//load and after reload 
//		nsIDOMNodeList children = getContentArea().getChildNodes();
//		long len = children.getLength();щ
//		for (long i = len - 1; i >= 0; i--) {
//			getContentArea().removeChild(children.item(i));
//		}

		if (sourceDocument != null) {
			buildDom(sourceDocument);
		}

	}

	// temporary, will be change to prefference's variable
	// private boolean borderVisible = true;

	/**
	 * Adds visual representation of {@code sourceNode} and its descendants
	 * to {@code visualContainer}.
	 * 
	 * If {@code visualNextNode} is not {@code null}, then created
	 * representation will be inserted before {@code visualNextNode}, 
	 * otherwise it will be inserted at the end of {@code visualContainer}
	 * 
	 * @param sourceNode source node, cannot be {@code null} 
	 * @param visualNextNode next visual node, can be {@code null}
	 * @param visualContainer visual container, cannot be {@code null} 
	 * @return {@code true} if and only if the visual representation is created and added successfully 
	 */
	private boolean addNode(Node sourceNode, nsIDOMNode visualNextNode, nsIDOMNode visualContainer) {
		/*
		 * Check includeStack size. During node creation it could be increased.
		 * !IMPORTANT! Thus the state should be check in the beginning 
		 * and stored until the visual element is added into DOM.
		 * For each node there should be a separate variable.
		 * !IMPORTANT! It is used to register nodes in VpeDomMapping.
		 */
		boolean onlyOneIncludeStack = isCurrentMainDocument(); 
		try {
			nsIDOMNode visualNewNode = createNode(sourceNode, visualContainer, onlyOneIncludeStack);
// Commented as fix for JBIDE-3012.	
//		// Fix for JBIDE-1097
//		try {
//			if (visualNewNode != null) {
//				nsIDOMHTMLInputElement iDOMInputElement = (nsIDOMHTMLInputElement) visualNewNode
//						.queryInterface(nsIDOMHTMLInputElement.NS_IDOMHTMLINPUTELEMENT_IID);
//				iDOMInputElement.setReadOnly(true);
//			}
//		} catch (XPCOMException ex) {
//			// just ignore this exception
//		}
				if (visualNewNode != null) {
					/*
					 * https://jira.jboss.org/jira/browse/JBIDE-3373 
					 * Do not add additional visual node for f:facet 
					 * when it is inserted into existing one.
					 */
					nsIDOMElement element = null;
					try {
						element = queryInterface(visualNewNode, nsIDOMElement.class);
					} catch (org.mozilla.xpcom.XPCOMException e) {
						/*
						 * Cannot parse node to element, do nothing
						 */
					}
					if (!((null != element) && element.hasAttribute(VPE_FACET))) {
						nsIDOMNode registeredVisualNewNode = null;
						if (visualNextNode == null) {
							registeredVisualNewNode = visualContainer.appendChild(visualNewNode);
						} else {
							registeredVisualNewNode = visualContainer.insertBefore(visualNewNode, visualNextNode);
						}
						/*
						 * https://issues.jboss.org/browse/JBIDE-9932
						 * Update visual node in vpe mapping.
						 */
						if (onlyOneIncludeStack) {
							domMapping.remapVisualNode(visualNewNode, registeredVisualNewNode);
						}
					}
					return true;
				}
		} catch (XPCOMException xpcomException) {
			VpePlugin.reportProblem(xpcomException);
		}
		return false;
	}
	
	/**
	 * Creates new visual node representing {@code sourceNode} and its descendants.
	 * 
	 * @param sourceNode source node
	 * @param visualOldContainer visual node, in which the caller plans to insert new visual node 
	 * @return new visual node
	 */
	public nsIDOMNode createNode(Node sourceNode, nsIDOMNode visualOldContainer, 
			boolean onlyOneIncludeStack) throws VpeDisposeException {
		//it's check for initialization visualController,
		//if we trying to process some event when controller
		//hasn't been initialized, it's causes 
		//org.eclipse.ui.PartInitException: Warning: Detected recursive 
		//attempt by part org.jboss.tools.jst.jsp.jspeditor.HTMLTextEditor to create itself 
		//(this is probably, but not necessarily, a bug)

		if(visualEditor.getController().getSelectionManager()!=null) {
			// reads and dispatch events, this code prevent eclipse
			//from sleeping during processing big pages
			VpePageContext.processDisplayEvents();
		}
		// JBIDE-675, checks if editor was disposed or not
		if (getPageContext().getSourceBuilder() == null
				|| includeDocuments == null) {
			throw new VpeDisposeException();
		}
		/*
		 * 1) source node can be changed and link can be a null in this case --
		 * we shouldn't process this node
		 * 2) https://issues.jboss.org/browse/JBIDE-9827
		 * Every source node's change/update causes a new update job, 
		 * every update job is put to the queue.
		 * When there is the update job for the same node in the queue -- 
		 * the oldest one is removed from the queue, and then sourceNode could be null. 
		 */
		if ((sourceNode==null)
				||(sourceNode.getNodeType() != Node.TEXT_NODE
	 			&& sourceNode.getNodeType() != Node.ELEMENT_NODE
	 			&& sourceNode.getNodeType() != Node.COMMENT_NODE 
	 			&& sourceNode.getNodeType() != Node.CDATA_SECTION_NODE
				&& sourceNode.getNodeType() != Node.DOCUMENT_NODE)) {
			return null;
		}
		
		Set<Node> ifDependencySet = new HashSet<Node>();
		pageContext.setCurrentVisualNode(visualOldContainer);
		VpeTemplate template = getTemplateManager().getTemplate(pageContext,
				sourceNode, ifDependencySet);
		VpeCreationData creationData = null;
		Node sourceNodeProxy = null;
		// FIX FOR JBIDE-1568, added by Max Areshkau
		try {
			if (getPageContext().getElService().isELNode(sourceNode)) {
				sourceNodeProxy = VpeProxyUtil.createProxyForELExpressionNode(
						getPageContext(), sourceNode);
				try {
					creationData = template.create(getPageContext(),
							sourceNodeProxy, getVisualDocument());
					//Fix for JBIDE-3144, we use proxy and some template can 
					//try to cast for not supported interface
				} catch(ClassCastException ex) {
					VpePlugin.reportProblem(ex);
					sourceNodeProxy = null;
					//then we create template without using proxy
					creationData = template.create(getPageContext(), 
							sourceNode, getVisualDocument());
				}
			} else {
				creationData = template.create(getPageContext(), sourceNode,
						getVisualDocument());
			}
		} catch (XPCOMException ex) {
			VpePlugin.getPluginLog().logError(ex);
			VpeTemplate defTemplate = getTemplateManager().getDefTemplate();
			creationData = defTemplate.create(
					getPageContext(), sourceNode, getVisualDocument());
		} catch (RuntimeException ex) {
			VpePlugin.getPluginLog().logError(ex);
			VpeTemplate defTemplate = getTemplateManager().getDefTemplate();
			creationData = defTemplate.create(
					getPageContext(), sourceNode, getVisualDocument());
		}
		if (creationData == null) {
			VpePlugin.getDefault().logError(
					"!ERROR! VpeCreationData is not initialized for source node '" //$NON-NLS-1$
							+ sourceNode.getNodeName() + "'"); //$NON-NLS-1$
			VpeTemplate defTemplate = getTemplateManager().getDefTemplate();
			creationData = defTemplate.create(
					getPageContext(), sourceNode, getVisualDocument());
		}
		getPageContext().setCurrentVisualNode(null);
		/*
		 * JBDS crashes when 'creationData' is null
		 */
		nsIDOMNode visualNewNode = creationData.getNode();
		if (sourceNode.getNodeType() == Node.ELEMENT_NODE && visualNewNode == null && isShowInvisibleTags()) {
			visualNewNode = createInvisbleElementLabel(sourceNode);
		}
		if (visualNewNode != null
				&& visualNewNode.getNodeType() == nsIDOMNode.ELEMENT_NODE) {
			nsIDOMElement visualNewElement = queryInterface(visualNewNode, nsIDOMElement.class);
			if ((visualNewElement != null) && template.hasImaginaryBorder()) {
				visualNewElement.setAttribute(HTML.ATTR_STYLE, visualNewElement
						.getAttribute(HTML.ATTR_STYLE)
						+ VpeStyleUtil.SEMICOLON_STRING + DOTTED_BORDER);
			}
			if (visualNewElement != null) {
				correctVisualAttribute(visualNewElement);
			}
			/*
			 * Create border for unknown tags if specified.
			 * Update the style attribute. Usually it's DIV or SPAN with text, 
			 * so it's harmless to update the style.
			 * For more complex action #createBorder(..) method should be used.
			 *	Also "Create border for all tags" option is never used so it was removed. 
			 */
			if ((template.getType() == VpeHtmlTemplate.TYPE_ANY) && showBorderForUnknownTags) {
					String style = visualNewElement
						.getAttribute(VpeStyleUtil.ATTRIBUTE_STYLE);
					style += CSS_STYLE_FOR_BORDER_FOR_UNKNOWN_TAGS;
					visualNewElement.setAttribute(VpeStyleUtil.ATTRIBUTE_STYLE, style);
					
			}
			if (!isCurrentMainDocument() && visualNewElement != null) {
				setReadOnlyElement(visualNewElement);
			}
		}

		if (sourceNode instanceof Element && visualNewNode != null
				&& visualNewNode.getNodeType() == nsIDOMNode.ELEMENT_NODE) {
			setTooltip((Element) sourceNode, queryInterface(visualNewNode, nsIDOMElement.class));
		}
		VpeElementMapping elementMapping = null;
		/*
		 * Even when 'visualNewNode=null' VpeElementMapping
		 * should be created. It influences on the visual refresh.
		 */
		if (onlyOneIncludeStack) {
			final VpeElementData data = creationData.getElementData();
			if ((sourceNodeProxy != null) && (data != null)
					&& (data.getNodesData() != null)
					&& (data.getNodesData().size() > 0)) {
				for (org.jboss.tools.vpe.editor.mapping.NodeData nodeData : data.getNodesData()) {
					if (nodeData.getSourceNode() != null) {
						Node attr = null;
						if(sourceNode.getAttributes()!=null) {
							attr = sourceNode.getAttributes().getNamedItem(
									nodeData.getSourceNode().getNodeName());
						} else {
							//Text node haven't child nodes, but it's  node.
							attr = sourceNode;
						}
						nodeData.setSourceNode(attr);
						nodeData.setEditable(false);
					}
				}
			}
			elementMapping = new VpeElementMapping(
					sourceNode, visualNewNode, template,
					ifDependencySet, creationData.getData(), data);
			registerNodes(elementMapping);
		}
		/*
		 * When in templates xml file specified that particular template
		 * cannot have children -- childrenInfoList will be ignored.
		 * But tags could have other html tags in value attribute. 
		 * And while creating the template they will be put into childrenInfoList.
		 * Thus childrenInfoList should be checked in any way!
		 */
		List<VpeChildrenInfo> childrenInfoList = creationData.getChildrenInfoList();
		if (template.hasChildren()) {
			if (childrenInfoList == null) {
				addChildren(template, sourceNode,
						visualNewNode != null ? visualNewNode : visualOldContainer);
			} else {
				addChildren(template, sourceNode, visualOldContainer, childrenInfoList);
			}
		} else {
			/*
			 * https://issues.jboss.org/browse/JBIDE-9417
			 * Template has no children, but could add
			 * any additional children from childrenInfoList.
			 * Implies that templates that should have no children
			 * also should have null or empty childrenInfoList.
			 */
			if (childrenInfoList != null) {
				addChildren(template, sourceNode, visualOldContainer, childrenInfoList);
			}
			if ((sourceNode.getNodeType() == Node.ELEMENT_NODE)
					&& (visualNewNode != null) && isShowInvisibleTags()) {
				nsIDOMElement span =  getVisualDocument().createElement(HTML.TAG_SPAN);
				span.appendChild(visualNewNode);
				addChildren(template, sourceNode,span);
				visualNewNode= span;
			}
		}
		getPageContext().setCurrentVisualNode(visualOldContainer);
		try {
			template.validate(getPageContext(), sourceNode, getVisualDocument(), creationData);
		} catch (RuntimeException ex) {
			VpePlugin.getPluginLog().logError(ex);
		}
		getPageContext().setCurrentVisualNode(null);
		return visualNewNode;
	}

	protected void correctVisualAttribute(nsIDOMElement element) {
		String styleValue = element.getAttribute(HTML.ATTR_STYLE);
		String backgroundValue = element.getAttribute(HTML.ATTR_BACKGROUND);
		if (styleValue != null) {
			styleValue = VpeStyleUtil.addFullPathIntoURLValue(styleValue, pageContext);
			element.setAttribute(HTML.ATTR_STYLE, styleValue);
		}
		if (backgroundValue != null) {
			backgroundValue = VpeStyleUtil.addFullPathIntoBackgroundValue(
					backgroundValue, pageContext.getEditPart().getEditorInput());
			element.setAttribute(HTML.ATTR_BACKGROUND, backgroundValue);
		}
		//fix for jbide-3209		
		if(element.hasAttribute(HTML.ATTR_DIR)) {
			element.removeAttribute(HTML.ATTR_DIR);
		}
	}

	/**
	 * Adds visual representations of {@code sourceContainer}'s children 
	 * and their descendants to {@code visualContainer}.
	 * 
	 * @param containerTemplate
	 * @param sourceContainer
	 * @param visualContainer
	 */
	protected void addChildren(VpeTemplate containerTemplate,
			Node sourceContainer, nsIDOMNode visualContainer) {
		NodeList sourceNodes = sourceContainer.getChildNodes();
		int len = sourceNodes.getLength();
		int childrenCount = 0;
		for (int i = 0; i < len; i++) {
			Node sourceNode = sourceNodes.item(i);
			if (addNode(sourceNode, null, visualContainer)) {
//				if (Node.ELEMENT_NODE == sourceNode.getNodeType()) { }
				childrenCount++;
			}
		}
		if (childrenCount == 0) {
			setPseudoContent(containerTemplate, sourceContainer,visualContainer);
		}
	}

	protected void addChildren(VpeTemplate containerTemplate,
			Node sourceContainer, nsIDOMNode visualOldContainer,
			List<?> childrenInfoList) {
		for (int i = 0; i < childrenInfoList.size(); i++) {
			VpeChildrenInfo info = (VpeChildrenInfo) childrenInfoList.get(i);
			nsIDOMNode visualParent = info.getVisualParent();
			if (visualParent == null) {
				visualParent = visualOldContainer;
			}
			List<?> sourceChildren = info.getSourceChildren();
			int childrenCount = 0;
			if (sourceChildren != null) {
				for (int j = 0; j < sourceChildren.size(); j++) {
					Node child = (Node) sourceChildren.get(j);
					if ((!isInvisibleNode(child)) 
							&& addNode(child, null, visualParent)) {
						childrenCount++;
					}
				}
			}
			if (childrenCount == 0 && childrenInfoList.size() == 0) {
				setPseudoContent(containerTemplate, sourceContainer,
						visualParent);
			}
		}
	}

	/**
	 * 
	 * @param node
	 * @return
	 */
	private boolean isInvisibleNode(Node node) {

		// get template
		Set<Node> ifDependencySet = new HashSet<Node>();
		VpeTemplate template = getTemplateManager().getTemplate(pageContext, node,
				ifDependencySet);
		// check if invisible tag
		if (template.isInvisible()) {
			return true;
		}
		return false;

	}

	// /////////////////////////////////////////////////////////////////////////
	public nsIDOMNode addStyleNodeToHead(String styleText) {
		nsIDOMNode newStyle = getVisualDocument()
				.createElement(VpeStyleUtil.ATTRIBUTE_STYLE);

		if (styleText != null) {
			nsIDOMText newText = getVisualDocument().createTextNode(styleText);
			newStyle.appendChild(newText);
		}
		getHeadNode().appendChild(newStyle);
		return newStyle;
	}

	public nsIDOMNode replaceStyleNodeToHead(nsIDOMNode oldStyleNode,
			String styleText) {
		nsIDOMElement newStyle = getVisualDocument()
				.createElement(VpeStyleUtil.ATTRIBUTE_STYLE);

		if (styleText != null) {
			nsIDOMNode newText = getVisualDocument().createTextNode(styleText);
			newStyle.appendChild(newText);
		}

		getHeadNode().replaceChild(newStyle, oldStyleNode);
		return newStyle;
	}

	public void removeStyleNodeFromHead(nsIDOMNode oldStyleNode) {
		getHeadNode().removeChild(oldStyleNode);
	}

	void addExternalLinks() {
		IEditorInput input = pageContext.getEditPart().getEditorInput();
		IFile file = null;
		if (input instanceof IFileEditorInput) {
			file = ((IFileEditorInput) input).getFile();
		}
		ResourceReference[] l = null;
		if (file != null) {
			l = CSSReferenceList.getInstance().getAllResources(file);
		}
		if (l != null) {
			for (ResourceReference item : l) {
				addLinkNodeToHead("file:///" + item.getLocation(), YES_STRING, false); //$NON-NLS-1$
			}
		}
	}

	void removeExternalLinks() {
		nsIDOMNodeList childs = getHeadNode().getChildNodes();
		long length = childs.getLength();
		for (long i = length - 1; i >= 0; i--) {
			nsIDOMNode node = childs.item(i);
			if (node.getNodeType() == nsIDOMNode.ELEMENT_NODE) {
				boolean isLink = false;
				boolean isStyle = false;
				if ((isLink = HTML.TAG_LINK
						.equalsIgnoreCase(node.getNodeName()))
						|| (isStyle = HTML.TAG_STYLE.equalsIgnoreCase(node
								.getNodeName()))) {
					nsIDOMElement element = queryInterface(node, nsIDOMElement.class);
					if ((isLink || (isStyle && ATTR_VPE_INLINE_LINK_VALUE
							.equalsIgnoreCase(element.getAttribute(ATTR_VPE))))
							&& YES_STRING
									.equalsIgnoreCase(element
											.getAttribute(VpeTemplateManager.ATTR_LINK_EXT))) {
						getHeadNode().removeChild(node);
					}
				}
			}
		}
	}

	void refreshExternalLinks() {
		removeExternalLinks();
		addExternalLinks();
	}

	public static boolean isPseudoElement(nsIDOMNode visualNode) {
		if (visualNode == null) {
			return false;
		}

		if (visualNode.getNodeType() != Node.ELEMENT_NODE) {
			return false;
		}

		if (YES_STRING.equalsIgnoreCase((queryInterface(visualNode, nsIDOMElement.class))
				.getAttribute(PSEUDO_ELEMENT_ATTR))) {
			return true;
		}

		return false;
	}

	private void setPseudoContent(VpeTemplate containerTemplate,
			Node sourceContainer, nsIDOMNode visualContainer) {
		if (containerTemplate != null) {
			try {
				containerTemplate.setPseudoContent(pageContext, sourceContainer,
					visualContainer, getVisualDocument());
			} catch (RuntimeException ex) {
				VpePlugin.getPluginLog().logError(ex);
			}
		} else {
			try {
				VpeDefaultPseudoContentCreator.getInstance().setPseudoContent(
						pageContext, sourceContainer, visualContainer,
						getVisualDocument());
			} catch (VpeExpressionException ex) {
				VpeExpressionException exception = new VpeExpressionException(
						"Error for source node" + sourceContainer.toString(), ex); //$NON-NLS-1$
				VpePlugin.reportProblem(exception);
			}
		}

	}

	public boolean isEmptyElement(nsIDOMNode visualParent) {
		nsIDOMNodeList visualNodes = visualParent.getChildNodes();
		long len = visualNodes.getLength();

		if ((len == 0) || (len == 1 && isEmptyText(visualNodes.item(0)))) {
			return true;
		}

		return false;
	}

	public boolean isEmptyDocument() {
		nsIDOMNodeList visualNodes = getContentArea().getChildNodes();
		long len = visualNodes.getLength();
		if ((len == 0)
				|| (len == 1 && (isEmptyText(visualNodes.item(0)) || isPseudoElement(visualNodes
						.item(0))))) {
			return true;
		}

		return false;
	}

	private boolean isEmptyText(nsIDOMNode visualNode) {
		if (visualNode == null
				|| (visualNode.getNodeType() != nsIDOMNode.TEXT_NODE)) {
			return false;
		}

		if (visualNode.getNodeValue().trim().length() == 0) {
			return true;
		}

		return false;
	}

	// ==========================================================

	public void updateNode(Node sourceNode) {
		if (sourceNode == null) {
			return;
		}

		switch (sourceNode.getNodeType()) {
		case Node.DOCUMENT_NODE:
			rebuildDom((Document) sourceNode);
			break;
		default:
			updateElement(sourceNode);
		}
	}

	private void updateElement(Node sourceNode) {
		VpeElementMapping elementMapping = null;
		VpeNodeMapping nodeMapping = domMapping.getNodeMapping(sourceNode);
		if (nodeMapping instanceof VpeElementMapping) {
			elementMapping = (VpeElementMapping) nodeMapping;
			if (elementMapping != null && elementMapping.getTemplate() != null) {
				/*
				 * https://issues.jboss.org/browse/JBIDE-10089
				 * <style> is updated in a special way:
				 * There is no need in removing and adding its visual node.
				 * Thus the underneath statement is reasonable here.
				 */
				if (HTML.TAG_STYLE.equalsIgnoreCase(sourceNode.getNodeName())) {
					VpeStyleUtil.refreshStyleElement(this, elementMapping);
					return;
				}
				Node updateNode = elementMapping.getTemplate().getNodeForUpdate(
						pageContext,elementMapping.getSourceNode(),
						elementMapping.getVisualNode(),elementMapping.getData());
				if ((updateNode != null) && (updateNode != sourceNode)) {
					updateNode(updateNode);
					return;
				}
			}
		}
		/*
		 * 1) Remove source node from mappings and lists.
		 */
		nsIDOMNode visualOldNode = domMapping.remove(sourceNode);
		getSourceNodes().remove(sourceNode);
		if (sourceNode instanceof INodeNotifier) {
			((INodeNotifier) sourceNode).removeAdapter(getSorceAdapter());
		}
		/*
		 * 2) Add new visual node for this source node.
		 */
		if (visualOldNode != null) {
			nsIDOMNode visualContainer = visualOldNode.getParentNode();
			nsIDOMNode visualNextNode = visualOldNode.getNextSibling();
			if (visualContainer != null) {
				addNode(sourceNode, visualNextNode, visualContainer);
				// If add the new node after deleting the old, in some cases
				// XULRunner will work in unexpected way (see JBIDE-3473)
				// so it is necessary to remove the old child AFTER adding the new 
				visualContainer.removeChild(visualOldNode);
			}
		} else {
			// Max Areshkau Why we need update parent node when we update text
			// node?
			// looks like we haven't need do it.
			if (sourceNode.getNodeType() == Node.TEXT_NODE) {
				updateNode(sourceNode.getParentNode());
			}else if(HTML.TAG_LINK.equalsIgnoreCase(sourceNode.getNodeName())) {
				addNode(sourceNode, null, getHeadNode());
			}
		}
	}

	public void removeNode(Node sourceNode) {
		// remove from cash should be called first
		domMapping.remove(sourceNode);
		getSourceNodes().remove(sourceNode);
		if (sourceNode instanceof INodeNotifier) {
			((INodeNotifier) sourceNode).removeAdapter(getSorceAdapter());
		}
	}

	public void setCdataText(Node sourceNode) {
		Node sourceParent = sourceNode.getParentNode();
		if (sourceParent != null && sourceParent.getLocalName() != null) {
			String sourceParentName = sourceParent.getLocalName();
			if (Docbook.ELEMENT_PROGRAMLISTING.equalsIgnoreCase(sourceParentName)) {
				updateNode(sourceParent);
			}
		}
	}

	public boolean setText(Node sourceText) {
		Node sourceParent = sourceText.getParentNode();
		if (sourceParent != null && sourceParent.getLocalName() != null) {
			String sourceParentName = sourceParent.getLocalName();
			if (HTML.TAG_TEXTAREA.equalsIgnoreCase(sourceParentName)
					|| HTML.TAG_OPTION.equalsIgnoreCase(sourceParentName)
					|| HTML.TAG_STYLE.equalsIgnoreCase(sourceParentName)) {
				updateNode(sourceText.getParentNode());
				return true;
			}
		}
		nsIDOMNode visualText = domMapping.getVisualNode(sourceText);
		if (visualText != null) {
			String visualValue = TextUtil.visualText(sourceText.getNodeValue());
			visualText.setNodeValue(visualValue);
		} else {
			VpeNodeMapping nodeMapping = domMapping
					.getNodeMapping(sourceParent);
			if (nodeMapping instanceof VpeElementMapping) {
				VpeTemplate template = ((VpeElementMapping) nodeMapping)
						.getTemplate();
				if (template != null) {
					if (!template.containsText()) {
						return false;
					}
				}
			}
			updateNode(sourceText);
			return true;
		}
		return false;
	}

	public void setAttribute(Element sourceElement, String name, String value) {
		VpeElementMapping elementMapping = (VpeElementMapping) domMapping
				.getNodeMapping(sourceElement);
		/*
		 * https://jira.jboss.org/jira/browse/JBIDE-4110
		 * Update any template automatically on attribute adding.
		 */
		if (elementMapping != null) {
		    updateElement(sourceElement);
		}
	}

	public void stopToggle(Node sourceNode) {
		if (!(sourceNode instanceof Element)) {
			return;
		}

		Element sourceElement = (Element) sourceNode;
		VpeElementMapping elementMapping = (VpeElementMapping) 
				domMapping.getNodeMapping(sourceElement);
		/*
		 * https://issues.jboss.org/browse/JBIDE-9790
		 */
		if ((elementMapping != null) && 
				(elementMapping.getTemplate() instanceof VpeTemplateSafeWrapper)) {
			VpeToggableTemplate toggableTemplate = (VpeToggableTemplate) ((VpeTemplateSafeWrapper) 
					elementMapping.getTemplate()).getAdapter(VpeToggableTemplate.class);
			if (toggableTemplate != null) {
				toggableTemplate.stopToggling(sourceElement);
			}
		}
	}

	public Element doToggle(nsIDOMNode visualNode) {
		if (visualNode == null) {
			return null;
		}
		nsIDOMElement visualElement = null;
		try {
			visualElement = queryInterface(visualNode, nsIDOMElement.class);
		} catch (XPCOMException exception) {
			visualElement = queryInterface(visualNode.getParentNode(), nsIDOMElement.class);
		}
		if (visualElement == null) {
			return null;
		}

		nsIDOMAttr toggleIdAttr = visualElement
				.getAttributeNode(VPE_USER_TOGGLE_ID);
		if (toggleIdAttr == null) {
			return null;
		}
		String toggleId = toggleIdAttr.getNodeValue();
		if (toggleId == null) {
			return null;
		}

		boolean toggleLookup = false;
		nsIDOMAttr toggleLookupAttr = visualElement
				.getAttributeNode(VPE_USER_TOGGLE_LOOKUP_PARENT);
		if (toggleLookupAttr != null) {
			toggleLookup = "true".equals(toggleLookupAttr.getNodeValue()); //$NON-NLS-1$
		}

		List<nsIDOMNode> list = xulRunnerEditor.getSelectedNodes();
		nsIDOMNode selectedElem = null;
		if (list.size() > 0) {
			selectedElem = list.get(0);
		}
		// Fixes JBIDE-1823 author dmaliarevich
		if (null == selectedElem) {
			return null;
		}
		VpeElementMapping elementMapping = null;
		VpeNodeMapping nodeMapping = domMapping.getNodeMapping(selectedElem);
		if (nodeMapping instanceof VpeElementMapping) {
			elementMapping = (VpeElementMapping) nodeMapping;
		}
		// end of fix
		Node sourceNode = domMapping.getSourceNode(selectedElem);
		if (sourceNode == null) {
			return null;
		}
		Element sourceElement = (Element) (sourceNode instanceof Element 
				? sourceNode
				: sourceNode.getParentNode());
		/*
		 * Fixes JBIDE-1823
		 * Template is looked according to <code>selectedElem</code>
		 * so <code>toggleLookupAttr</code> should be retrieved
		 * from this element
		 */
		nsIDOMNamedNodeMap m = selectedElem.getAttributes();
		if (m != null) {
			nsIDOMNode attr = m.getNamedItem(VPE_USER_TOGGLE_LOOKUP_PARENT);
			if (attr != null) {
				toggleLookup = "true".equalsIgnoreCase(attr.getNodeValue()); //$NON-NLS-1$
			}
		} // end of fix JBIDE-1823
		if (elementMapping != null) {
			VpeToggableTemplate toggableTemplate = null;
			/*
			 * https://issues.jboss.org/browse/JBIDE-9790
			 */
			if (elementMapping.getTemplate() instanceof VpeTemplateSafeWrapper) {
				toggableTemplate = (VpeToggableTemplate) ((VpeTemplateSafeWrapper)
						elementMapping.getTemplate()).getAdapter(VpeToggableTemplate.class);
			} // End of fix JBIDE-9790 
			while (toggleLookup && (sourceElement != null) && (toggableTemplate == null)) {
				sourceElement = (Element) sourceElement.getParentNode();
				if (sourceElement == null) {
					break;
				}
				// Fixes JBIDE-1823
				nodeMapping = domMapping.getNodeMapping(sourceElement);
				if (nodeMapping instanceof VpeElementMapping) {
					elementMapping = (VpeElementMapping) nodeMapping;
				} // end of fix JBIDE-1823
				if (elementMapping == null) {
					continue;
				}
				/*
				 * https://issues.jboss.org/browse/JBIDE-9790
				 */
				if (elementMapping.getTemplate() instanceof VpeTemplateSafeWrapper) {
					toggableTemplate = (VpeToggableTemplate) ((VpeTemplateSafeWrapper)
							elementMapping.getTemplate()).getAdapter(VpeToggableTemplate.class);
				} // End of fix JBIDE-9790
			}
			if (toggableTemplate != null) {
				toggableTemplate.toggle(this, sourceElement, toggleId);
				updateElement(sourceElement);
				return sourceElement;
			}
		}
		return null;
	}

	public void removeAttribute(Element sourceElement, String name) {
		VpeElementMapping elementMapping = (VpeElementMapping) domMapping
				.getNodeMapping(sourceElement);
		/*
		 * https://jira.jboss.org/jira/browse/JBIDE-4110
		 * Update any template automatically on attribute deleting.
		 */
		if (elementMapping != null) {
		    updateElement(sourceElement);
		}
	}

	public void refreshBundleValues(Element sourceElement) {
		VpeElementMapping elementMapping = (VpeElementMapping) domMapping
				.getNodeMapping(sourceElement);
		if (elementMapping != null) {
			VpeTemplate template = elementMapping.getTemplate();
			template.refreshBundleValues(pageContext, sourceElement,
					elementMapping.getData());
		}
	}

	boolean isContentArea(nsIDOMNode visualNode) {
		return getContentArea().equals(visualNode);
	}

	public nsIDOMElement getContentArea() {
		return visualEditor.getContentArea();
	}

	public void setSelectionRectangle(/* nsIDOMElement */List<nsIDOMNode> visualNodes) {
		int resizerConstrains = VpeTagDescription.RESIZE_CONSTRAINS_NONE;
		if(visualNodes.size()==1){
			 resizerConstrains = getResizerConstrains(visualNodes.get(0));
		}
		visualEditor.setSelectionRectangle(visualNodes, resizerConstrains);
	}

    /**
     * 
     * @param href_val
     * @param ext_val
     * @param firstElement
     *            true - first node in head, false - last node
     * @return
     */
    public nsIDOMNode addLinkNodeToHead(String href_val, String ext_val,
	    boolean firstElement) {
	nsIDOMElement newNode = createInlineStyleNode(href_val,
		ATTR_REL_STYLESHEET_VALUE, ext_val);

	// TODO Dzmitry Sakovich
	// Fix priority CSS classes JBIDE-1713
	if (firstElement) {
	    nsIDOMNode firstNode = getHeadNode().getFirstChild();
	    getHeadNode().insertBefore(newNode, firstNode);
	} else {
	    getHeadNode().appendChild(newNode);
	}
	return newNode;
    }

	public nsIDOMNode replaceLinkNodeToHead(nsIDOMNode oldNode,
			String href_val, String ext_val) {
		nsIDOMNode newNode = createInlineStyleNode(href_val,
				ATTR_REL_STYLESHEET_VALUE, ext_val);
		getHeadNode().replaceChild(newNode, oldNode);
		return newNode;
	}

	public nsIDOMNode replaceLinkNodeToHead(String href_val, String ext_val, boolean firstElement) {
		nsIDOMNode newNode = null;
		nsIDOMNode oldNode = getLinkNode(href_val, ext_val);
		if (oldNode == null) {
			newNode = addLinkNodeToHead(href_val, ext_val, firstElement);
		}
		return newNode;
	}

	public void removeLinkNodeFromHead(nsIDOMNode node) {
		getHeadNode().removeChild(node);
	}

	private nsIDOMElement createInlineStyleNode(String href_val, String rel_val, String ext_val) {
		nsIDOMElement inlineStyle = null;
		if (ATTR_REL_STYLESHEET_VALUE.equalsIgnoreCase(rel_val)
				&& href_val.startsWith("file:")) { //$NON-NLS-1$
			/*
			 * Because of the Mozilla caches the linked css files we replace tag
			 * <link rel="styleseet" href="file://..."> with tag <style
			 * vpe="ATTR_VPE_INLINE_LINK_VALUE">file content</style> It is
			 * LinkReplacer
			 */
			inlineStyle = getVisualDocument().createElement(HTML.TAG_STYLE);
			inlineStyle.setAttribute(ATTR_VPE, ATTR_VPE_INLINE_LINK_VALUE);

			/* Copy links attributes into our <style> */
			inlineStyle.setAttribute(VpeTemplateManager.ATTR_LINK_HREF, href_val);
			inlineStyle.setAttribute(VpeTemplateManager.ATTR_LINK_EXT, ext_val);
			BufferedReader in = null;
			try {
				StringBuilder styleText = new StringBuilder(EMPTY_STRING);
				URL url = new URL((new Path(href_val)).toOSString());
				String fileName = url.getFile();
				in = new BufferedReader(new FileReader((fileName)));
				String str = EMPTY_STRING;
				while ((str = in.readLine()) != null) {
					styleText.append(str);
				}
				in.close();

				String styleForParse = styleText.toString();
				/*
				 * https://issues.jboss.org/browse/JBIDE-5861
				 * Remove CSS comments first:
				 */
				styleForParse = VpeStyleUtil.removeAllCssComments(styleForParse);
				List<String> imports = VpeStyleUtil.findCssImportConstruction(styleForParse, pageContext);
				if (imports.size() > 0) {
					for (String key : imports) {
						/*
						 * Add nested @import constructions
						 */
						addLinkNodeToHead(key, "css_nested_import_construction", false); //$NON-NLS-1$
					}
					/*
					 * Replace @import constructions
					 */
					styleForParse = VpeStyleUtil.removeAllCssImportConstructions(styleForParse);
				}
				styleForParse = VpeStyleUtil.addFullPathIntoURLValue(styleForParse, href_val);
				inlineStyle.appendChild(getVisualDocument().createTextNode(styleForParse));
				return inlineStyle;
			} catch (FileNotFoundException fnfe) {
				/* File which was pointed by user is not exists. Do nothing. */
			} catch (IOException ioe) {
				VpePlugin.getPluginLog().logError(ioe.getMessage(), ioe);
			} finally {
				if (in != null) {
					try {
						in.close();
					} catch (IOException e) {
						VpePlugin.getPluginLog().logError(e);
					}
				}
			}
		}

		inlineStyle = getVisualDocument().createElement(HTML.TAG_LINK);
		inlineStyle.setAttribute(VpeTemplateManager.ATTR_LINK_REL, rel_val);
		inlineStyle.setAttribute(VpeTemplateManager.ATTR_LINK_HREF, href_val);
		inlineStyle.setAttribute(VpeTemplateManager.ATTR_LINK_EXT, ext_val);

		return inlineStyle;
	}

	private boolean isLinkReplacer(nsIDOMNode node) {
		return HTML.TAG_STYLE.equalsIgnoreCase(node.getNodeName())
				&& ATTR_VPE_INLINE_LINK_VALUE
						.equalsIgnoreCase((queryInterface(node, nsIDOMElement.class))
								.getAttribute(ATTR_VPE));
	}

	private nsIDOMNode getLinkNode(String href_val, String ext_val) {
		nsIDOMNodeList children = getHeadNode().getChildNodes();
		long len = children.getLength();
		for (long i = len - 1; i >= 0; i--) {
			nsIDOMNode node = children.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				if (HTML.TAG_LINK.equalsIgnoreCase(node.getNodeName())
						|| isLinkReplacer(node)) {
					nsIDOMElement element = queryInterface(node, nsIDOMElement.class);
					if (ext_val.equalsIgnoreCase(element
							.getAttribute(VpeTemplateManager.ATTR_LINK_EXT))
							&& href_val
									.equalsIgnoreCase(element
											.getAttribute(VpeTemplateManager.ATTR_LINK_HREF))) {
						return node;
					}
				}
			}
		}
		return null;
	}

	private void cleanHead() {
		//Fix for JBIDE-3205, mareshkau
		if(getHeadNode()==null) {
			return;
		}
		nsIDOMNodeList children = getHeadNode().getChildNodes();
		long len = children.getLength();
		for (long i = len - 1; i >= 0; i--) {
			nsIDOMNode node = children.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				if (isLinkReplacer(node)) {
					/*
					 * Added by Max Areshkau(Fix for JBIDE-1941) Ext. attribute
					 * used for adding external styles to editor. If was added
					 * external attribute, this property is true.
					 */
					if (!YES_STRING.equalsIgnoreCase((queryInterface(node, nsIDOMElement.class))
							.getAttribute(VpeTemplateManager.ATTR_LINK_EXT))) {
						// int linkAddress =
						// MozillaSupports.queryInterface(node,
						// nsIStyleSheetLinkingElement.
						// NS_ISTYLESHEETLINKINGELEMENT_IID);
						// nsIStyleSheetLinkingElement linkingElement = new
						// nsIStyleSheetLinkingElement(linkAddress);
						// linkingElement.removeStyleSheet();
						node = getHeadNode().removeChild(node);
					}
				} else if (HTML.TAG_STYLE.equalsIgnoreCase(node.getNodeName())
						&& (!YES_STRING
								.equalsIgnoreCase((queryInterface(node, nsIDOMElement.class))
										.getAttribute(ATTR_VPE)))) {
					node = getHeadNode().removeChild(node);
				}
			}
		}
		
	}

	private int getResizerConstrains(nsIDOMNode visualNode) {
		VpeNodeMapping nodeMapping = domMapping.getNodeMapping(visualNode);
		if (nodeMapping != null
				&& (nodeMapping instanceof VpeElementMapping)
				&& (nodeMapping.getSourceNode() instanceof Element)
				&& (nodeMapping.getVisualNode().getNodeType() == nsIDOMNode.ELEMENT_NODE)) {
			return ((VpeElementMapping) nodeMapping).getTemplate()
					.getTagDescription(
							pageContext,
							(Element) nodeMapping.getSourceNode(),
							getVisualDocument(),
							queryInterface(nodeMapping.getVisualNode(), nsIDOMElement.class),
							((VpeElementMapping) nodeMapping).getData())
					.getResizeConstrains();
		}
		return VpeTagDescription.RESIZE_CONSTRAINS_NONE;
	}

	public void resize(nsIDOMElement element, int constrains, int top,
			int left, int width, int height) {
		VpeElementMapping elementMapping = (VpeElementMapping) domMapping
				.getNodeMapping(element);
		if (elementMapping != null) {
			elementMapping.getTemplate().resize(pageContext,
					(Element) elementMapping.getSourceNode(), getVisualDocument(),
					element, elementMapping.getData(), constrains, top,
					left, width, height);
		}
	}

	public static boolean isAnonElement(nsIDOMNode visualNode) {
		if (visualNode != null
				&& visualNode.getNodeType() == nsIDOMNode.ELEMENT_NODE) {
			String attrValue = (queryInterface(visualNode, nsIDOMElement.class))
					.getAttribute(MOZ_ANONCLASS_ATTR);

			return attrValue != null && attrValue.length() > 0;
		}

		return false;
	}

	public void innerDrop(Node dragNode, Node container, int offset) {
		VpeNodeMapping mapping = domMapping.getNearNodeMapping(container);
		if (mapping != null) {
			nsIDOMNode visualDropContainer = mapping.getVisualNode();
			// switch (mapping.getType()) {
			// case VpeNodeMapping.TEXT_MAPPING:
			// break;
			// case VpeNodeMapping.ELEMENT_MAPPING:
			nsIDOMNode visualParent = visualDropContainer.getParentNode();
			VpeNodeMapping oldMapping = mapping;
			mapping = domMapping.getNearNodeMapping(visualParent);
			if (mapping instanceof VpeElementMapping) {
				((VpeElementMapping) mapping).getTemplate().innerDrop(
						pageContext,
						new VpeSourceInnerDragInfo(dragNode, 0, 0),
						new VpeSourceDropInfo(container, offset, true));
			} else {
				if (oldMapping instanceof VpeElementMapping) {
					((VpeElementMapping) oldMapping).getTemplate().innerDrop(
							pageContext,
							new VpeSourceInnerDragInfo(dragNode, 0, 0),
							new VpeSourceDropInfo(container, offset, true));
				} else {
					/* TODO: implement this case or completely
					 * remove this method?
					 * At the time of writing this comment 
					 * the implementation of template.innerDrop() method above
					 * was empty, so there are no differences between
					 * calling this method and doing nothing.
					 */
				}
			}
			// }

		}
	}

	public static boolean isTextEditable(nsIDOMNode visualNode) {

		if (visualNode != null) {
			nsIDOMNode parent = visualNode.getParentNode();
			if (parent != null
					&& parent.getNodeType() == nsIDOMNode.ELEMENT_NODE) {
				nsIDOMElement element = queryInterface(parent, nsIDOMElement.class);
				nsIDOMAttr style = element.getAttributeNode("style"); //$NON-NLS-1$
				if (style != null) {
					String styleValue = style.getNodeValue();
					String[] items = styleValue.split(";"); //$NON-NLS-1$
					for (int i = 0; i < items.length; i++) {
						String[] item = items[i].split(":"); //$NON-NLS-1$
						if ("-moz-user-modify".equals(item[0].trim()) //$NON-NLS-1$
								&& "read-only".equals(item[1].trim())) { //$NON-NLS-1$
							return false;
						}
					}
				}
				nsIDOMAttr classAttr = element.getAttributeNode("class"); //$NON-NLS-1$
				if (classAttr != null) {
					String classValue = classAttr.getNodeValue().trim();
					if ("__any__tag__caption".equals(classValue)) { //$NON-NLS-1$
						return false;
					}
				}
			}
		}
		return true;
	}

	protected void setTooltip(Element sourceElement, nsIDOMElement visualElement) {
		if (visualElement != null && sourceElement != null
				&& !((IDOMElement) sourceElement).isJSPTag()) {
			if (HTML.TAG_HTML.equalsIgnoreCase(sourceElement.getNodeName())) {
				return;
			}
			String titleValue = getTooltip(sourceElement);

			if (titleValue != null) {
				titleValue = titleValue.replaceAll("&", "&amp;"); //$NON-NLS-1$ //$NON-NLS-2$
				titleValue = titleValue.replaceAll("<", "&lt;"); //$NON-NLS-1$ //$NON-NLS-2$
				titleValue = titleValue.replaceAll(">", "&gt;"); //$NON-NLS-1$ //$NON-NLS-2$
			}

			if (titleValue != null) {
				// visualElement.setAttribute("title", titleValue);
				setTooltip(visualElement, titleValue);
			}
		}
	}

	protected void setTooltip(nsIDOMElement visualElement, String titleValue) {
		visualElement.setAttribute(HTML.ATTR_TITLE, titleValue);
		nsIDOMNodeList children = visualElement.getChildNodes();
		long len = children.getLength();
		for (long i = 0; i < len; i++) {
			nsIDOMNode child = children.item(i);
			if (child.getNodeType() == nsIDOMNode.ELEMENT_NODE) {
				setTooltip((queryInterface(child, nsIDOMElement.class)),
						titleValue);
			}
		}
	}
	
	private String getTooltip(Element sourceElement) {
		StringBuilder buffer = new StringBuilder();
		buffer.append(sourceElement.getNodeName());
		NamedNodeMap attrs = sourceElement.getAttributes();
		int len = attrs.getLength();
		for (int i = 0; i < len; i++) {
			if (i == 7) {
				return buffer.append("\n\t... ").toString(); //$NON-NLS-1$
			}
			int valueLength = attrs.item(i).getNodeValue().length();
			if (valueLength > 30) {
				StringBuilder temp = new StringBuilder();
				temp.append(attrs.item(i).getNodeValue().substring(0, 15))
					.append(" ... ") //$NON-NLS-1$
					.append(attrs.item(i).getNodeValue().substring(
								valueLength - 15, valueLength));
				buffer.append("\n").append(attrs.item(i).getNodeName()).append(": ").append(temp); //$NON-NLS-1$ //$NON-NLS-2$
			} else {
				buffer.append("\n").append(attrs.item(i).getNodeName()).append(": ") //$NON-NLS-1$ //$NON-NLS-2$
					  .append(attrs.item(i).getNodeValue());
			}

		}

		return buffer.toString();
	}

	public static nsIDOMNode getLastAppreciableVisualChild(nsIDOMNode visualParent) {
		nsIDOMNode visualLastChild = null;
		nsIDOMNodeList visualChildren = visualParent.getChildNodes();
		long len = visualChildren.getLength();
		for (long i = len - 1; i >= 0; i--) {
			nsIDOMNode visualChild = visualChildren.item(i);
			if (!isPseudoElement(visualChild) && !isAnonElement(visualChild)) {
				visualLastChild = visualChild;
				break;
			}
		}
		return visualLastChild;
	}

	public static boolean isIncludeElement(nsIDOMElement visualElement) {
		return YES_STRING.equalsIgnoreCase(visualElement
				.getAttribute(INCLUDE_ELEMENT_ATTR));
	}

	public static void markIncludeElement(nsIDOMElement visualElement) {
		visualElement.setAttribute(INCLUDE_ELEMENT_ATTR, YES_STRING);
	}

	protected void setReadOnlyElement(nsIDOMElement node) {
		String style = node.getAttribute(VpeStyleUtil.ATTRIBUTE_STYLE);
		style = VpeStyleUtil.setParameterInStyle(style, "-moz-user-modify", "read-only"); //$NON-NLS-1$ //$NON-NLS-2$
		node.setAttribute(VpeStyleUtil.ATTRIBUTE_STYLE, style);
	}
	
	public nsIDOMText getOutputTextNode(Attr attr) {
		Element sourceElement = attr.getOwnerElement();
		VpeElementMapping elementMapping = domMapping
				.getNearElementMapping(sourceElement);
		if (elementMapping != null) {

			return elementMapping.getTemplate().getOutputTextNode(pageContext,
					sourceElement, elementMapping.getData());
		}
		return null;
	}

	private nsIDOMElement getSelectedElement() {
		return xulRunnerEditor.getSelectedElement();
	}

	public void pushIncludeStack(VpeIncludeInfo includeInfo) {
		includeStack.add(includeInfo);
	}

	public VpeIncludeInfo popIncludeStack() {
		VpeIncludeInfo includeInfo = null;
		if (includeStack.size() > 0) {
			includeInfo = includeStack.remove(includeStack.size() - 1);
		}
		return includeInfo;
	}

	public boolean isFileInIncludeStack(IStorage file) {
		if (file == null) {
			return false;
		}
		for (int i = 0; i < includeStack.size(); i++) {
			if (file.equals((includeStack.get(i)).getStorage())) {
				return true;
			}
		}
		return false;
	}
	
	public boolean isCurrentMainDocument() {
		return includeStack.size() <= 1;
	}

	/**
	 * Can be a null in some cases, for example when we open an external file, see JBIDE-3030
	 * @return file include info
	 */
	public VpeIncludeInfo getCurrentIncludeInfo() {
		if (includeStack.size() <= 0) {
			return null;
		}
		return includeStack.get(includeStack.size() - 1);
	}

	@Override
	public void dispose() {
		clearIncludeDocuments();
		includeDocuments = null;
		cleanHead();
		domMapping.clear(getContentArea());
		pageContext.dispose();
		super.dispose();
	}

	private void clearIncludeDocuments() {
		Collection<Document> documents = includeDocuments.values();
		for (Document document : documents) {
			VpeCreatorUtil.releaseDocumentFromRead(document);
		}
		includeDocuments.clear();
	}

	/**
	 * @return the pageContext
	 */
	protected VpePageContext getPageContext() {
		return pageContext;
	}

	/**
	 * @return the visualDocument
	 */
	protected nsIDOMDocument getVisualDocument() {
		return visualEditor.getDomDocument();
	}

	/**
	 * @return the xulRunnerEditor
	 */
	public XulRunnerEditor getXulRunnerEditor() {
		return xulRunnerEditor;
	}

	public Map<IStorage, Document> getIncludeDocuments() {
		return includeDocuments;
	}

	public nsIDOMNode getHeadNode() {
		return visualEditor.getHeadNode();
	}

	public boolean isShowInvisibleTags() {
		return showInvisibleTags;
	}

	public void setShowInvisibleTags(boolean showInvisibleTags) {
		this.showInvisibleTags = showInvisibleTags;
	}
	
	public boolean isShowBorderForUnknownTags() {
		return showBorderForUnknownTags;
	}

	public void setShowBorderForUnknownTags(boolean showBorderForUnknownTags) {
		this.showBorderForUnknownTags = showBorderForUnknownTags;
	}

	/**
	 * 
	 * @param sourceNode
	 * @return
	 */
	public nsIDOMNode createInvisbleElementLabel(Node sourceNode) {
		nsIDOMElement span = getVisualDocument().createElement(HTML.TAG_SPAN);

		span.setAttribute(HTML.TAG_STYLE,
				"border: 1px dashed GREY; color: GREY; font-size: 12px;"); //$NON-NLS-1$

		nsIDOMText text = getVisualDocument().createTextNode(sourceNode
				.getNodeName());

		span.appendChild(text);

		return span;
	}

	public void clearSelectionRectangle() {
		setSelectionRectangle(VpeVisualDomBuilder.EMPTY_SELECTION);
	}
}
