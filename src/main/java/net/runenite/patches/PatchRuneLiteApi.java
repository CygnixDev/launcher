package net.runenite.patches;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import net.runelite.launcher.beans.Artifact;
import static net.runenite.ArtifactPatcher.RESOURCES_DIR;
import net.runenite.Patch;

@Slf4j
public class PatchRuneLiteApi extends Patch
{
	public static final File PATCH_FILES = new File(RESOURCES_DIR, "runelite-api");

	protected boolean hasChanges = false;

	@Override
	public boolean appliesTo(String artifactName)
	{
		return artifactName.startsWith("runelite-api-") && PATCH_FILES.exists();
	}

	@Override
	public void apply(Artifact artifact, File workingDir)
	{
		try (Stream<Path> paths = Files.walk(PATCH_FILES.toPath()))
		{
			for (Path path : (Iterable<Path>) paths::iterator)
			{
				if (writeFile(workingDir, path))
				{
					hasChanges = true;
				}
			}
		}
		catch (Exception e)
		{
			log.error("Error applying patch", e);
		}
	}

	private boolean writeFile(File workingDir, Path path) throws IOException
	{
		Path relativeFilePath = PATCH_FILES.toPath().relativize(path);

		Path targetPath = workingDir.toPath().resolve(relativeFilePath);
		if (targetPath.toFile().exists() || targetPath.toFile().isDirectory())
		{
			return false;
		}

		// noinspection ResultOfMethodCallIgnored
		targetPath.getParent().toFile().mkdirs();

		Files.copy(path, targetPath);
		log.info("Patched: " + relativeFilePath);

		return true;
	}
}
