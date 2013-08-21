package interdroid.cuckoo.eclipse.plugin;

import java.util.ArrayList;

import interdroid.cuckoo.eclipse.plugin.preferences.CuckooPreferencePage;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class CuckooPlugin extends AbstractUIPlugin {

	private static CuckooPlugin plugin;

	public CuckooPlugin() {
		super();
		plugin = this;
	}

	public void start(BundleContext context) throws Exception {
		super.start(context);
		IPreferenceStore store = getPreferenceStore();
		store.addPropertyChangeListener(new IPropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent event) {
				if (CuckooPreferencePage.CUCKOO_LOCATION.equals(event
						.getProperty())) {
					FixCuckooProjects();
				}
			}
		});
	}

	protected void FixCuckooProjects() {
		IJavaProject[] projects = getCuckooProjects();
		ClasspathContainerInitializer.updateProjects(projects);

	}

	public static IJavaProject[] getCuckooProjects() {
		IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
		IJavaModel javaModel = JavaCore.create(workspaceRoot);

		IJavaProject[] javaProjectList;
		try {
			javaProjectList = javaModel.getJavaProjects();
		} catch (JavaModelException jme) {
			return new IJavaProject[0];
		}

		ArrayList<IJavaProject> list = new ArrayList<IJavaProject>();

		for (IJavaProject javaProject : javaProjectList) {
			IProject project = javaProject.getProject();
			if (isCuckooProject(project)) {
				list.add(javaProject);
			}
		}

		return list.toArray(new IJavaProject[list.size()]);
	}

	public static boolean isCuckooProject(IProject project) {
		try {
			return project.hasNature(CuckooNature.NATURE_ID);
		} catch (CoreException e) {
			// ignore, just return false;
		}

		return false;
	}

	public void stop(BundleContext context) throws Exception {
		super.stop(context);
	}

	public static CuckooPlugin getDefault() {
		return plugin;
	}

}
