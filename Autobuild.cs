using System.IO;
using System.IO.Compression;
using System.Linq;
using UnityEditor;

public class Autobuild
{
    private const BuildTarget buildTarget = BuildTarget.StandaloneWindows;

  
    public static void GetProjectName()
    {
        var buildTargetLbl = GetBuildTargetLabel();

        string projectName = @$"acourier
{buildTargetLbl}";

        string tmpFilePath = "/tmp/unity_project_name.txt";

        File.WriteAllText(tmpFilePath, projectName);
    }

  
    internal static string GetBuildTargetLabel()
    {
        return buildTarget switch
        {
            BuildTarget.StandaloneWindows => "win",
            BuildTarget.Android => "android",
            BuildTarget.iOS => "ios",
            _ => "null-unity"
        };
    }

  
    public static void MakeBuild()
    {
        string resPath = "Builds/acourier";

        if (!Directory.Exists(resPath))
            Directory.CreateDirectory(resPath);

  
        BuildPlayerOptions options = new BuildPlayerOptions
        {
            scenes = GetScenesToBuild(),
            locationPathName = $"{resPath}/Build.exe",
            target = buildTarget,
            options = BuildOptions.None
        };

        BuildPipeline.BuildPlayer(options);

        ArchiveBuild(resPath);
    }


    private static string[] GetScenesToBuild()
    {
        return EditorBuildSettings.scenes.Where(t => t.enabled).Select(t => t.path).ToArray();
    }

  
    private static void ArchiveBuild(string buildPath)
    {
        string archivePath = buildPath + ".zip";

        if (File.Exists(archivePath))
            File.Delete(archivePath);

        ZipFile.CreateFromDirectory(buildPath, archivePath);
    }
}