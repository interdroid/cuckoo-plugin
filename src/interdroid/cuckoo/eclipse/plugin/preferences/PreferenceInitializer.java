package interdroid.cuckoo.eclipse.plugin.preferences;

import java.io.File;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import interdroid.cuckoo.eclipse.plugin.CuckooPlugin;

/**
 * Class used to initialize default preference values.
 */
public class PreferenceInitializer extends AbstractPreferenceInitializer {

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer#initializeDefaultPreferences()
     */
    public void initializeDefaultPreferences() {
	IPreferenceStore store = CuckooPlugin.getDefault().getPreferenceStore();
	store.setDefault(CuckooPreferencePage.CUCKOO_LOCATION, System.getProperty("user.home")
		+ File.separator + "cuckoo-library");
    }

}
