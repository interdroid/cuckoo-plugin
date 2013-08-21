package interdroid.cuckoo.eclipse.plugin;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.ApplicationWindow;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.android.ide.eclipse.adt.AdtPlugin;

@SuppressWarnings("restriction")
public class AddCuckooNature implements IObjectActionDelegate {

	private IJavaProject currentProject;

	@Override
	public void run(IAction arg0) {
		if (currentProject == null) {
			AdtPlugin.printErrorToConsole("error",
					"No current project! Cuckoo nature not added");
			return;
		}

		try {
			AdtPlugin.printToConsole(currentProject.getProject(),
					"Adding Cuckoo nature...");//$NON-NLS-1$

			IProjectDescription description = currentProject.getProject()
					.getDescription();
			String[] natures = description.getNatureIds();
			String[] newNatures = new String[natures.length + 1];
			System.arraycopy(natures, 0, newNatures, 1, natures.length);

			// Insert CuckooNature in front for proper use of logo.
			newNatures[0] = CuckooNature.NATURE_ID;
			description.setNatureIds(newNatures);
			currentProject.getProject().setDescription(description, null);

			IClasspathEntry[] rawClasspath = currentProject.getRawClasspath();

			List<IClasspathEntry> newEntries = new ArrayList<IClasspathEntry>();
			for (IClasspathEntry e : rawClasspath) {
				newEntries.add(e);
			}

			newEntries.add(JavaCore.newContainerEntry(ClasspathContainer.ID));

			currentProject.setRawClasspath(
					newEntries.toArray(new IClasspathEntry[newEntries.size()]),
					null);

			// Refresh project to rewrite AIDL files again.
			currentProject.getProject().refreshLocal(IResource.DEPTH_INFINITE,
					null);
			AdtPlugin.printToConsole(currentProject.getProject(),
					"Cuckoo nature added!");//$NON-NLS-1$
		} catch (Throwable e) {
			AdtPlugin.printToConsole(currentProject.getProject(),
					"Cuckoo nature addition failed! " + e);//$NON-NLS-1$
			AdtPlugin.logAndPrintError(e,
					"AddCuckooNature", "failed to finish.");//$NON-NLS-1$
		}
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
