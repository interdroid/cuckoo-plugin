package interdroid.cuckoo.eclipse.plugin;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.build.AidlProcessor;
import com.android.ide.eclipse.adt.internal.build.RenderScriptProcessor;
import com.android.ide.eclipse.adt.internal.build.SourceProcessor;
import com.android.ide.eclipse.adt.internal.build.builders.PreCompilerBuilder;

@SuppressWarnings("restriction")
public class CuckooBuilder extends PreCompilerBuilder {

	public static final String ID = "CuckooPlugin.CuckooBuilder";//$NON-NLS-1$

	private static final String PREFIX = "CuckooBuilder: ";//$NON-NLS-1$

	private static Field mProcessorsField = null;

	private static Field mAidlProcessorField = null;
	private static Field mRenderScriptProcessorField = null;

	private IProject project;

	// Make the field "mProcessors" of the PreCompilerBuilder visible, so that
	// we can replace AidlProcessor with CuckooProcessor.

	// update August 2013, things have changed with the new ADT, the list is
	// only filled at the first "build", but only when mAidlProcessor is null.
	// Therefore we initialize mAidlProcessor, but then we also have to do this
	// for the mRenderScript processor and put both of them in the mProcessor
	// list, which would otherwise be done during the first build.
	static {
		try {
			Class<?> cl = Class
					.forName("com.android.ide.eclipse.adt.internal.build.builders.PreCompilerBuilder");//$NON-NLS-1$
			mProcessorsField = cl.getDeclaredField("mProcessors");//$NON-NLS-1$
			mProcessorsField.setAccessible(true);
			mAidlProcessorField = cl.getDeclaredField("mAidlProcessor");
			mAidlProcessorField.setAccessible(true);
			mRenderScriptProcessorField = cl
					.getDeclaredField("mRenderScriptProcessor");
			mRenderScriptProcessorField.setAccessible(true);
		} catch (Throwable e) {
			// ignore.
		}

	}

	@SuppressWarnings({ "unchecked" })
	protected void startupOnInitialize() {
		super.startupOnInitialize();
		project = getProject();
		AdtPlugin.printToConsole(project, PREFIX + "initializing...");//$NON-NLS-1$

		IFolder mGenFolder = project
				.getFolder(com.android.SdkConstants.FD_GEN_SOURCES);

		IJavaProject javaProject = JavaCore.create(project);

		AidlProcessor cuckooProcessor = new CuckooProcessor(javaProject,
				mBuildToolInfo, mGenFolder);
		RenderScriptProcessor renderScriptProcessor = new RenderScriptProcessor(
				javaProject, mBuildToolInfo, mGenFolder);

		try {
			mAidlProcessorField.set(this, cuckooProcessor);
			mRenderScriptProcessorField.set(this, renderScriptProcessor);
			List<SourceProcessor> mProcessors = (List<SourceProcessor>) mProcessorsField
					.get(this);
			mProcessors.add(cuckooProcessor);
			mProcessors.add(renderScriptProcessor);
			AdtPlugin.printToConsole(project, PREFIX
					+ "Successfully inserted Cuckoo Builder");
		} catch (Exception e) {
			AdtPlugin
					.printToConsole(
							project,
							PREFIX
									+ "Failed to insert Cuckoo Builder, check /path/to/workspace/.metadata/.log for details");
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	protected IProject[] build(int kind, Map args, IProgressMonitor monitor)
			throws CoreException {
		IProject[] retval = super.build(kind, args, monitor);
		if (kind == IncrementalProjectBuilder.FULL_BUILD) {
			fullBuild(monitor);
		} else {
			IResourceDelta delta = getDelta(project);
			if (delta == null) {
				fullBuild(monitor);
			} else {
				incrementalBuild(delta, monitor);
			}
		}
		return retval;
	}

	private void incrementalBuild(IResourceDelta delta, IProgressMonitor monitor)
			throws CoreException {
		IPath path = project.getProjectRelativePath().append(
				File.separatorChar + "remote");
		IResourceDelta modifiedDelta = delta.findMember(path);
		if (modifiedDelta == null)
			return;

		IResourceDeltaVisitor visitor = new IResourceDeltaVisitor() {
			public boolean visit(IResourceDelta delta) throws CoreException {
				// only interested in content changes
				if ((delta.getFlags() & IResourceDelta.CONTENT) == 0)
					return true;
				IResource resource = delta.getResource();
				// only interested in files with the "java" extension
				if (resource.getType() == IResource.FILE
						&& "java".equalsIgnoreCase(resource.getFileExtension())) {
					CuckooProcessor.runAnt(project);
					return false;
				}
				return true;
			}
		};
		modifiedDelta.accept(visitor);
	}

	private void fullBuild(IProgressMonitor monitor) throws CoreException {
		CuckooProcessor.writeBuildFile(project);
		CuckooProcessor.runAnt(project);
	}
}
