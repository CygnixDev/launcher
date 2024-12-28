package net.runenite.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import lombok.extern.slf4j.Slf4j;
import net.runenite.RuneNiteLauncher;

@Slf4j
public class ResourceManager
{
	public static List<String> getResourcesAtPath(String resourceName)
	{
		if (!resourceName.endsWith("/"))
		{
			resourceName = resourceName + "/";
		}

		URL dirURL = RuneNiteLauncher.class.getResource(resourceName);
		if (dirURL == null)
		{
			return Collections.emptyList();
		}

		String protocol = dirURL.getProtocol();
		List<String> result = new ArrayList<>();

		if ("file".equals(protocol))
		{
			try
			{
				File dir = new File(dirURL.toURI());
				if (dir.isDirectory())
				{
					findFilePathsRecursively(dir, dir, result);
				}
			}
			catch (URISyntaxException e)
			{
				log.error("Error reading file resource", e);
			}
		}
		else if ("jar".equals(protocol))
		{
			try
			{
				JarURLConnection jarConnection = (JarURLConnection) dirURL.openConnection();
				try (JarFile jarFile = jarConnection.getJarFile())
				{
					String baseEntryName = jarConnection.getEntryName();

					if (baseEntryName != null && !baseEntryName.endsWith("/"))
					{
						baseEntryName = baseEntryName + "/";
					}

					Enumeration<JarEntry> entries = jarFile.entries();
					while (entries.hasMoreElements())
					{
						JarEntry entry = entries.nextElement();
						String entryName = entry.getName();

						if (baseEntryName != null
							&& entryName.startsWith(baseEntryName)
							&& !entry.isDirectory())
						{
							String relativePath = entryName.substring(baseEntryName.length());
							result.add(relativePath);
						}
					}
				}
			}
			catch (IOException e)
			{
				log.error("Error reading jar file", e);
			}
		}
		else
		{
			log.error("Unsupported protocol for resource listing: " + protocol);
		}

		return result;
	}

	private static void findFilePathsRecursively(File baseDir, File currentDir, List<String> result)
	{
		File[] files = currentDir.listFiles();
		if (files == null)
		{
			return;
		}

		for (File file : files)
		{
			if (file.isDirectory())
			{
				findFilePathsRecursively(baseDir, file, result);
			}
			else
			{
				Path relPath = baseDir.toPath().relativize(file.toPath());
				result.add(relPath.toString().replace("\\", "/"));
			}
		}
	}

	public static void copyResource(String name, File dest) throws IOException
	{
		try (InputStream is = RuneNiteLauncher.class.getResourceAsStream(name))
		{

			if (is == null)
			{
				throw new IOException("Resource not found: " + name);
			}

			Files.copy(is, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
