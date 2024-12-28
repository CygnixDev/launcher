package net.runenite.patches;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import net.runelite.launcher.beans.Artifact;
import static net.runenite.ArtifactPatcher.RESOURCES_DIR;
import net.runenite.Patch;

@Slf4j
public class PatchRuneLiteClient extends Patch
{
	public static final File PATCH_FILES = new File(RESOURCES_DIR, "client");

	protected boolean hasChanges = false;

	@Override
	public boolean appliesTo(String artifactName)
	{
		return artifactName.startsWith("client-") && PATCH_FILES.exists();
	}

	@Override
	public void apply(Artifact artifact, File workingDir)
	{
		try (Stream<Path> paths = Files.walk(workingDir.toPath()))
		{
			for (Path path : (Iterable<Path>) paths::iterator)
			{
				if (Files.isRegularFile(path))
				{
					if (patchFile(workingDir, path))
					{
						hasChanges = true;
					}
				}
			}
		}
		catch (Exception e)
		{
			log.error("Error applying patch", e);
		}
	}

	private boolean patchFile(File workingDir, Path path) throws IOException
	{
		Path relativeFilePath = workingDir.toPath().relativize(path);

		File patchFilePath = new File(PATCH_FILES, relativeFilePath.toString());
		if (!patchFilePath.exists())
		{
			return false;
		}

		File originalFilePath = patchFilePath.toPath().resolveSibling(patchFilePath.getName() + ".original").toFile();
		if (!originalFilePath.exists())
		{
			log.info("No .original comparison file found for: " + relativeFilePath);
			return false;
		}

		byte[] targetBytes = Files.readAllBytes(path);
		byte[] patchBytes = Files.readAllBytes(patchFilePath.toPath());

		if (Arrays.equals(targetBytes, patchBytes))
		{
			log.info("Already patched: " + relativeFilePath);
			return false;
		}

		byte[] originalBytes = Files.readAllBytes(originalFilePath.toPath());
		if (!Arrays.equals(originalBytes, targetBytes))
		{
			log.error("Original file does not match target file for: " + relativeFilePath);
			return false;
		}

		Files.write(path, patchBytes);
		log.info("Patched: " + relativeFilePath);

		return true;
	}
}
