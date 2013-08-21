package interdroid.cuckoo.eclipse.plugin;

import interdroid.cuckoo.eclipse.plugin.preferences.CuckooPreferencePage;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.util.ArrayList;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.preference.IPreferenceStore;

import com.android.ide.eclipse.adt.AdtPlugin;

// import com.android.ide.eclipse.adt.AdtPlugin;

public class ClasspathContainer implements IClasspathContainer {

	public static final IPath ID = new Path("CuckooPlugin.ClasspathContainer");

	private final FilenameFilter fileFilter = new FilenameFilter() {
		public boolean accept(File dir, String name) {
			// if the file extension is .jar return true, else false
			return name.endsWith(".jar");
		}
	};

	private IJavaProject project;

	public ClasspathContainer() {
	}

	public void setProject(IJavaProject project) {
		this.project = project;
	}

	@Override
	public IClasspathEntry[] getClasspathEntries() {

		IPreferenceStore store = CuckooPlugin.getDefault().getPreferenceStore();

		String path = store.getString(CuckooPreferencePage.CUCKOO_LOCATION);

		ArrayList<IClasspathEntry> list = new ArrayList<IClasspathEntry>();

		File directory = new File(path + File.separator + "lib/client");

		if (!directory.exists() || !directory.isDirectory()) {
			AdtPlugin.printToConsole(project.getProject(), "Warning: "
					+ directory.getName()
					+ " does not exist or is not a directory; Check your "
					+ CuckooPreferencePage.CUCKOO_LOCATION + " preference.");
			return new IClasspathEntry[0];
		}
		IFolder libsDir = project.getProject().getFolder("libs");
		if (!libsDir.exists()) {
			try {
				mkdir(libsDir);
			} catch (CoreException e) {
				AdtPlugin.printToConsole(project.getProject(),
						"Warning: Could not create libs directory");
				return new IClasspathEntry[0];
			}
		}

		try {
			File[] files = directory.listFiles(fileFilter);
			if (files != null) {
				for (File f : files) {
					if (!"android.jar".equals(f.getName())) {
						IFile target = libsDir.getFile(f.getName());
						copyFile(f, target);
						IClasspathEntry entry = JavaCore.newLibraryEntry(
								target.getFullPath(), null, null);
						if (entry != null) {
							list.add(entry);
						}
					}
				}
			}
		} catch (Exception e) {
			AdtPlugin.printToConsole(project.getProject(),
					"Warning: got exception " + e);
			return new IClasspathEntry[0];
		}
		return list.toArray(new IClasspathEntry[list.size()]);
	}

	private void copyFile(File source, IFile dest) throws CoreException,
			FileNotFoundException {
		if (dest.exists()) {
			return;
		}
		dest.create(new BufferedInputStream(new FileInputStream(source)),
				false, null);
	}

	private void mkdir(IFolder folder) throws CoreException {
		if (folder.exists()) {
			return;
		}
		folder.create(false, true, null);
	}

	@Override
	public String getDescription() {
		return "Cuckoo client library path";
	}

	@Override
	public int getKind() {
		return K_APPLICATION;
	}

	@Override
	public IPath getPath() {
		return ID;
	}

}
