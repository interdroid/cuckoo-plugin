package interdroid.cuckoo.eclipse.plugin.preferences;

import org.eclipse.jface.preference.*;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.IWorkbench;
import interdroid.cuckoo.eclipse.plugin.CuckooPlugin;

public class CuckooPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    public static String CUCKOO_LOCATION = "interdroid.cuckoo.location";//$NON-NLS-1$

    public CuckooPreferencePage() {
	super(GRID);
	setPreferenceStore(CuckooPlugin.getDefault().getPreferenceStore());
	setDescription("Cuckoo preferences");
    }

    public void createFieldEditors() {
	addField(new DirectoryFieldEditor(CUCKOO_LOCATION, "Cuckoo Library Location", getFieldEditorParent()));
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
     */
    public void init(IWorkbench workbench) {
    }

}