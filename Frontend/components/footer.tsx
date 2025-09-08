import { Heart } from "lucide-react"

export function Footer() {
  return (
    <footer className="border-t bg-card mt-12">
      <div className="container mx-auto px-4 py-6">
        <div className="flex items-center justify-center space-x-2 text-sm text-muted-foreground">
          <span>Made with</span>
          <Heart className="w-4 h-4 text-red-500 fill-current" />
          <span>by</span>
          <span className="font-semibold text-foreground">Parth Singh</span>
        </div>
      </div>
    </footer>
  )
}
