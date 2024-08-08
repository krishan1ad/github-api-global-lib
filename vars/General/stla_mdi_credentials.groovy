if (env.SYNTAX_CHECK) { return }

stlaMdiCredentials = [
    [$class: 'UsernamePasswordMultiBinding', credentialsId: 'GITHUB_CREDENTIALS', usernameVariable: 'GITHUB_USER', passwordVariable: 'GITHUB_TOKEN'],
    [$class: 'UsernamePasswordMultiBinding', credentialsId: 'GITHUB_REST_USER_CREDENTIALS', usernameVariable: 'GITHUB_REST_USER', passwordVariable: 'GITHUB_REST_PWD']
    [$class: 'UsernamePasswordMultiBinding', credentialsId: 'JFROG_STLA_CREDENTIALS', usernameVariable: 'JFROG_USER', passwordVariable: 'JFROG_PASSWOED'],
    [$class: 'UsernamePasswordMultiBinding', credentialsId: 'JFROG_MD_CREDENTIALS', usernameVariable: 'JFROG_USER', passwordVariable: 'JFROG_TOKEN'],
]

return this;