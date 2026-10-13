"""Merge the design files into one JSON for the simulator, and build the page."""
import json, yaml, pathlib
here = pathlib.Path(__file__).parent
slice_ = yaml.safe_load(open(here / '../slice1-mechanical.yaml'))
mb = yaml.safe_load(open(here / '../multiblocks.yaml'))
data = {
    "meta": {"slice": slice_["meta"]["slice"], "version": slice_["meta"]["version"], "multiblocks_version": mb["meta"]["version"]},
    "items": slice_["items"],
    "machines": slice_["machines"],
    "recipes": slice_["recipes"],
    "framework": mb["framework"],
    "framework_builds": mb["builds"],
    "multiblocks": mb["machines"],
}
json.dump(data, open(here / 'data.json', 'w'), default=str, indent=1)
core = open(here / 'sim-core.js').read().split('if (typeof module')[0]
page = open(here / 'page.template.html').read().replace('/*SIMCORE*/', core).replace('/*DATA*/', json.dumps(data, default=str))
open(here / 'index.html', 'w').write(page)
print("built data.json and index.html,", len(page), "bytes")
