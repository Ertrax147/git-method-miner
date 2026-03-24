import ast
import javalang
import redis
import os
import re
import time
from github import Github

# Conexión a Redis
r = redis.Redis(host=os.getenv("REDIS_HOST", "redis"), port=6379, db=0)
token = os.getenv("GITHUB_TOKEN")

def split_identifier(name):
    # CamelCase y snake_case
    s1 = re.sub('(.)([A-Z][a-z]+)', r'\1 \2', name)
    s2 = re.sub('([a-z0-9])([A-Z])', r'\1 \2', s1)
    return s2.replace('_', ' ').lower().split()

def extract_methods(content, ext):
    names = []
    try:
        if ext == '.py':
            tree = ast.parse(content)
            for node in ast.walk(tree):
                if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                    names.append(node.name)
        elif ext == '.java':
            tree = javalang.parse.parse(content)
            for path, node in tree.filter(javalang.tree.MethodDeclaration):
                names.append(node.name)
    except: pass
    return names

def run():
    if not token:
        print("Miner ERROR: No se encontró GITHUB_TOKEN en las variables de entorno.")
        return

    from github import Auth
    auth = Auth.Token(token)
    g = Github(auth=auth)

    print("Miner: Conectado a GitHub con éxito.")
    
    try:
        # repositorios mas populares
        repos = g.search_repositories(query='stars:>1', sort='stars', order='desc')
        print("Miner: Búsqueda de repositorios completada. Empezando análisis...")

        for repo in repos:
            print(f"\n[+] Analizando repositorio: {repo.full_name}")
            print(f"    Estrellas: {repo.stargazers_count}")

            try:
                contents = repo.get_contents("")
                archivos_procesados = 0

                while contents:
                    file_item = contents.pop(0)
                    
                    if file_item.type == "dir":
                        # Si es carpeta exploramos lo que hay dentro
                        if len(contents) < 200: 
                            contents.extend(repo.get_contents(file_item.path))
                    
                    elif file_item.name.endswith(('.py', '.java')):
                        ext = '.py' if file_item.name.endswith('.py') else '.java'
                        
                        # Descarga y procesa
                        try:
                            code = file_item.decoded_content.decode(errors='ignore')
                            nombres_metodos = extract_methods(code, ext)
                            
                            for nombre in nombres_metodos:
                                palabras = split_identifier(nombre)
                                for p in palabras:
                                    # Envia a Redis
                                    r.lpush('word_queue', p)
                            
                            archivos_procesados += 1
                            # Corregido: archivos_procesados para coincidir con la definición
                            if archivos_procesados % 5 == 0:
                                print(f"    ... {archivos_procesados} archivos analizados")
                        
                        except Exception:
                            continue # Si un archivo falla, seguimos con el siguiente

                print(f"    ✔ Finalizado: {repo.full_name} ({archivos_procesados} archivos)")

            except Exception as e:
                print(f"    [!] Error en el repo {repo.full_name}: {e}")
                continue

            # 2 segundos entre repositorios para evitar bloqueos de la API
            time.sleep(2)

    except Exception as e:
        print(f"Miner FATAL ERROR: {e}")

if __name__ == "__main__":
    run()