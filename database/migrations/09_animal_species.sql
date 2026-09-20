-- Especies extra para adopción y perdidos (además de perro, gato, ave y otro).
ALTER TYPE animal_species ADD VALUE IF NOT EXISTS 'RABBIT';
ALTER TYPE animal_species ADD VALUE IF NOT EXISTS 'HAMSTER';
ALTER TYPE animal_species ADD VALUE IF NOT EXISTS 'FISH';
ALTER TYPE animal_species ADD VALUE IF NOT EXISTS 'TURTLE';
