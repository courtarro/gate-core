package gate.util.maven;

import static gate.util.maven.Utils.getRepositoryList;
import static gate.util.maven.Utils.getRepositorySession;
import static gate.util.maven.Utils.getRepositorySystem;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;

public class SimpleMavenCache implements WorkspaceReader, Serializable {

	private static final long serialVersionUID = 8612094868614282978L;

	private File head;

	private SimpleMavenCache tail;

	private transient WorkspaceRepository repo;

	public SimpleMavenCache(File... dir) {
		
		if (dir == null || dir.length == 0) {
			throw new NullPointerException("At least one workspace directory must be specified");
		}
		
		head = dir[0];

		if (dir.length > 1) {
			tail = new SimpleMavenCache(Arrays.copyOfRange(dir, 1, dir.length));
		}
	}

	private File getArtifactFile(Artifact artifact) {
		File file = head;

		for (String part : artifact.getGroupId().split("\\.")) {
			file = new File(file, part);
		}

		file = new File(file, artifact.getArtifactId());

		file = new File(file, artifact.getVersion());

		file = new File(file, artifact.getArtifactId() + "-" + artifact.getVersion() + "." + artifact.getExtension());

		return file;
	}

	@Override
	public File findArtifact(Artifact artifact) {

		File file = getArtifactFile(artifact);

		if (file.exists())
			return file;

		if (tail == null)
			return null;

		return tail.findArtifact(artifact);
	}

	@Override
	public List<String> findVersions(Artifact artifact) {
		List<String> versions = new ArrayList<String>();

		if (tail != null) {
			versions.addAll(tail.findVersions(artifact));
		}

		File file = getArtifactFile(artifact).getParentFile().getParentFile();

		if (!file.exists() || !file.isDirectory())
			return versions;

		for (File version : file.listFiles()) {
			if (version.isDirectory())
				versions.add(version.getName());
		}

		return versions;
	}

	public void cacheArtifact(Artifact artifact) throws IOException, SettingsBuildingException,
			DependencyCollectionException, DependencyResolutionException {

		Dependency dependency = new Dependency(artifact, "runtime");

		RepositorySystem repoSystem = getRepositorySystem();
		RepositorySystemSession repoSession = getRepositorySession(repoSystem, null);

		CollectRequest collectRequest = new CollectRequest(dependency, getRepositoryList());

		DependencyNode node = repoSystem.collectDependencies(repoSession, collectRequest).getRoot();

		DependencyRequest dependencyRequest = new DependencyRequest();
		dependencyRequest.setRoot(node);

		DependencyResult result = repoSystem.resolveDependencies(repoSession, dependencyRequest);

		for (ArtifactResult ar : result.getArtifactResults()) {
			File file = getArtifactFile(ar.getArtifact());

			// file.getParentFile().mkdirs();
			//System.out.println(ar.getArtifact().getFile());

			FileUtils.copyFile(ar.getArtifact().getFile(), file);
		}
	}

	@Override
	public WorkspaceRepository getRepository() {
		if (repo == null) {
			repo = new WorkspaceRepository();
		}
		return repo;
	}

	public static void main(String args[]) throws Exception {

		for (RemoteRepository repo : Utils.getRepositoryList()) {
			System.out.println(repo);
		}

		Artifact artifactObj = new DefaultArtifact("uk.ac.gate.plugins", "annie", "jar", "8.5-SNAPSHOT");
		//artifactObj = artifactObj.setFile(
		//		new File("/home/mark/.m2/repository/uk/ac/gate/plugins/annie/8.5-SNAPSHOT/annie-8.5-SNAPSHOT.jar"));

		SimpleMavenCache reader = new SimpleMavenCache(new File("repo"));
		System.out.println(reader.findArtifact(artifactObj));
		System.out.println(reader.findVersions(artifactObj));
		reader.cacheArtifact(artifactObj);
		System.out.println(reader.findArtifact(artifactObj));
		System.out.println(reader.findVersions(artifactObj));
		
		reader = new SimpleMavenCache(new File("repo2"), new File("repo"));
		System.out.println(reader.findArtifact(artifactObj));
		System.out.println(reader.findVersions(artifactObj));
	}

}
