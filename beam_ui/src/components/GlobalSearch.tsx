import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { Search } from "lucide-react";
import {
  CommandDialog, CommandInput, CommandList, CommandEmpty,
  CommandGroup, CommandItem, CommandSeparator,
} from "@/components/ui/command";

export function GlobalSearch() {
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  useEffect(() => {
    const down = (e: KeyboardEvent) => {
      if ((e.key === "k" || e.key === "K") && (e.metaKey || e.ctrlKey)) {
        e.preventDefault();
        setOpen((o) => !o);
      }
    };
    document.addEventListener("keydown", down);
    return () => document.removeEventListener("keydown", down);
  }, []);

  const handleSelect = (path: string) => {
    setOpen(false);
    navigate(path);
  };

  const assets = (queryClient.getQueryData<any[]>(["assets"]) || []);
  const zones = (queryClient.getQueryData<any[]>(["zones"]) || []);

  return (
    <>
      <button
        onClick={() => setOpen(true)}
        className="flex items-center gap-1.5 px-2.5 py-1.5 text-xs text-muted-foreground hover:text-foreground hover:bg-muted rounded-md transition-colors"
        title="Search (⌘K)"
      >
        <Search className="h-3.5 w-3.5" />
        <span className="hidden sm:inline opacity-60">⌘K</span>
      </button>

      <CommandDialog open={open} onOpenChange={setOpen}>
        <CommandInput placeholder="Search assets or zones..." />
        <CommandList>
          <CommandEmpty>No results found.</CommandEmpty>
          {assets.length > 0 && (
            <>
              <CommandGroup heading="Assets">
                {assets.slice(0, 8).map((a: any) => (
                  <CommandItem key={a.id} onSelect={() => handleSelect(`/assets/${a.id}`)}>
                    <span>{a.name}</span>
                    {a.zone_name && <span className="ml-auto text-xs text-muted-foreground">{a.zone_name}</span>}
                  </CommandItem>
                ))}
              </CommandGroup>
              <CommandSeparator />
            </>
          )}
          {zones.length > 0 && (
            <CommandGroup heading="Zones">
              {zones.slice(0, 8).map((z: any) => (
                <CommandItem key={z.id} onSelect={() => handleSelect(`/zones/${z.id}`)}>
                  <span>{z.name}</span>
                  <span className="ml-auto text-xs text-muted-foreground">{z.asset_count} assets</span>
                </CommandItem>
              ))}
            </CommandGroup>
          )}
        </CommandList>
      </CommandDialog>
    </>
  );
}
