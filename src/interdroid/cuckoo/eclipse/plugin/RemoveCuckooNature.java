package interdroid.cuckoo.eclipse.plugin;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;

import com.android.ide.eclipse.adt.AdtPlugin;

public class RemoveCuckooNature implements IObjectActionDelegate {

	private IJavaProject currentProject;

	@Override
	public void run(IAction arg0) {
		if (currentProject == null) {
			AdtPlugin.printErrorToConsole("error",
					"No current project! Cuckoo nature not removed");
			return;
		}
		AdtPlugin.printToConsole(currentProject.getProject(),
				"Removing Cuckoo nature...");//$NON-NLS-1$
		try {
			IClasspathEntry[] rawClasspath = currentProject.getRawClasspath();
			List<IClasspathEntry> newEntries = new ArrayList<IClasspathEntry>();
			for (IClasspathEntry e : rawClasspath) {
				if (!e.getPath().equals(ClasspathContainer.ID)) {
					newEntries.add(e);
				}
			}

			currentProject.setRawClasspath(
					newEntries.toArray(new IClasspathEntry[newEntries.size()]),
					null);

			IProjectDescription description = currentProject.getProject()
					.getDescription();
			String[] natures = description.getNatureIds();
			description.setNatureIds(removeCuckooNature(natures));
			currentProject.getProject().setDescription(description, null);
			// refresh project so user sees changes
			currentProject.getProject().refreshLocal(IResource.DEPTH_INFINITE,
					null);
			AdtPlugin.printToConsole(currentProject.getProject(),
					"Cuckoo nature removed");//$NON-NLS-1$
		} catch (Throwable e) {
			AdtPlugin.printErrorToConsole(currentProject.getProject(),
					"Cuckoo nature removal failed! " + e);//$NON-NLS-1$
		}
	}

	private String[] removeCuckooNature(String[] natures) {
		ArrayList<String> list = new ArrayList<String>();
		for (String nature : natures) {
			if (!nature.equalsIgnoreCase(CuckooNature.NATURE_ID)) {
				list.add(nature);
			}
		}
		return list.toArray(new String[list.size()]);
	}

	@Override
	public void selectionChanged(IAction arg0, ISelection selection) {
		if (selection instanceof IStructuredSelection) {
			IStructuredSelection ss = (IStructuredSelection) selection;
			Object obj = ss.getFirstElement();
			if (obj instanceof IJavaProject) {
				currentProject = (IJavaProject) obj;
			}
		}

	}

	@Override
	public void setActivePart(IAction arg0, IWorkbenchPart arg1) {
		// nothing
	}

}
