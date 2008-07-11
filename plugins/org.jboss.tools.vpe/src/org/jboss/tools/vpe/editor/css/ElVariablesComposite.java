package org.jboss.tools.vpe.editor.css;

import java.util.List;

/**
 * The Class ElVariablesComposite.
 */
public class ElVariablesComposite extends ResourceReferencesComposite {

    /**
     * Creates the table provider.
     * 
     * @param dataList the data list
     * 
     * @return the resource references table provider
     */
    @Override
    protected ResourceReferencesTableProvider createTableProvider(List dataList) {
        return ResourceReferencesTableProvider.getELTableProvider(dataList);
    };


    /**
     * Gets the entity.
     * 
     * @return the entity
     */
    @Override
    protected String getEntity() {
        return (file != null) ? "VPEElReference" : "VPEElReferenceExt";
    }

    /**
     * Gets the reference list.
     * 
     * @return the reference list
     */
    @Override
    protected ResourceReferenceList getReferenceList() {
        return ELReferenceList.getInstance();
    }

}