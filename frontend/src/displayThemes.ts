export type DisplayThemeId = 'lcd' | 'sncf'

export type DisplayTheme = {
  id: DisplayThemeId
  label: string
  description: string
}

export const displayThemes: DisplayTheme[] = [
  {
    id: 'lcd',
    label: 'LCD transport',
    description: 'Contraste eleve et lecture rapide',
  },
  {
    id: 'sncf',
    label: 'Grande ligne',
    description: 'Affichage editorial et hierarchise',
  },
]
